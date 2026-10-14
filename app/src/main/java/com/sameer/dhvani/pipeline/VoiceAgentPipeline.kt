package com.sameer.dhvani.pipeline

import android.content.Context
import android.util.Log
import com.sameer.dhvani.audio.AudioCaptureManager
import com.sameer.dhvani.audio.AudioPlaybackManager
import com.sameer.dhvani.llm.GroqClient
import com.sameer.dhvani.llm.SentenceChunker
import com.sameer.dhvani.stt.WhisperRecognizer
import com.sameer.dhvani.tts.PiperSynthesizer
import com.sameer.dhvani.utils.LanguageDetector
import com.sameer.dhvani.utils.LatencyMetrics
import com.sameer.dhvani.utils.LatencyTracker
import com.sameer.dhvani.utils.SpokenLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AgentState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val language: SpokenLanguage? = null,
    val latency: LatencyMetrics? = null
)

enum class MessageSender {
    USER,
    AGENT
}

class VoiceAgentPipeline(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceAgentPipeline"
    }

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentMetrics = MutableStateFlow(LatencyMetrics())
    val currentMetrics: StateFlow<LatencyMetrics> = _currentMetrics.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    @Volatile
    private var isProcessing = false
    private var activePipelineJob: Job? = null

    // Subsystems
    private val audioCapture: AudioCaptureManager
    private val audioPlayback = AudioPlaybackManager()
    private val whisperRecognizer = WhisperRecognizer(context)
    private val piperSynthesizer = PiperSynthesizer(context)
    private val groqClient = GroqClient()
    private val latencyTracker = LatencyTracker()

    init {
        audioCapture = AudioCaptureManager(
            context = context,
            onSpeechStart = { handleSpeechStart() },
            onSpeechUtterance = { samples -> handleSpeechUtterance(samples) },
            onAudioLevel = { level -> _audioLevel.value = level }
        )
    }

    fun start() {
        isProcessing = false
        audioCapture.startListening()
        _agentState.value = AgentState.IDLE
    }

    fun stop() {
        activePipelineJob?.cancel()
        activePipelineJob = null
        audioCapture.stopListening()
        audioPlayback.bargeIn()
        groqClient.cancel()
        isProcessing = false
        _agentState.value = AgentState.IDLE
    }

    /**
     * Called by VAD when user begins speaking.
     * Implements Barge-in: immediately stops agent speech if talking.
     */
    private fun handleSpeechStart() {
        if (_agentState.value == AgentState.SPEAKING) {
            Log.d(TAG, "Barge-in detected! Stopping agent speech.")
            audioPlayback.bargeIn()
            groqClient.cancel()
            activePipelineJob?.cancel()
            activePipelineJob = null
            isProcessing = false
            audioCapture.resumeListening()  // Re-enable VAD for new speech
            _agentState.value = AgentState.LISTENING
        } else if (!isProcessing) {
            _agentState.value = AgentState.LISTENING
        }
    }

    /**
     * Called by VAD when silence is detected after user speech.
     */
    private fun handleSpeechUtterance(samples: FloatArray) {
        val currentState = _agentState.value
        // 1. State guard & processing flag: ignore if already processing or not in a valid state
        if (isProcessing || (currentState != AgentState.LISTENING && currentState != AgentState.IDLE)) {
            Log.d(TAG, "Ignoring utterance: pipeline is busy (state=$currentState, isProcessing=$isProcessing)")
            return
        }

        isProcessing = true
        _agentState.value = AgentState.THINKING
        audioCapture.pauseListening()  // Stop VAD while processing
        latencyTracker.reset()
        latencyTracker.markSpeechEnd()

        activePipelineJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                // 2. Cloud STT (Groq Whisper Large V3) with on-device Whisper Tiny fallback
                val (transcript, _) = try {
                    val cloudResult = groqClient.transcribeAudio(samples)
                    if (cloudResult.first.isNotBlank()) {
                        cloudResult
                    } else {
                        Log.w(TAG, "Cloud STT returned blank, falling back to on-device Whisper Tiny")
                        whisperRecognizer.transcribe(samples)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud STT failed, falling back to on-device Whisper Tiny", e)
                    whisperRecognizer.transcribe(samples)
                }

                if (transcript.isBlank()) {
                    Log.d(TAG, "Transcript is blank, returning to IDLE")
                    coroutineScope.launch {
                        _agentState.value = AgentState.IDLE
                        isProcessing = false
                        audioCapture.resumeListening()
                    }
                    return@launch
                }

                latencyTracker.markSttEnd()

                // 3. Language Detection (English vs Hinglish vs Hindi)
                val detectedLang = LanguageDetector.detectLanguage(transcript)

                // 4. Conversation History (last 5 messages before the current one)
                val conversationHistory = _messages.value.takeLast(5).map { msg ->
                    val role = when (msg.sender) {
                        MessageSender.USER -> "user"
                        MessageSender.AGENT -> "assistant"
                    }
                    Pair(role, msg.text)
                }

                // Add user message to UI
                val userMsg = ChatMessage(
                    sender = MessageSender.USER,
                    text = transcript,
                    language = detectedLang
                )
                coroutineScope.launch {
                    _messages.value = _messages.value + userMsg
                }

                // 5. Cloud LLM with Streaming Sentence-Chunked TTS
                val sentenceChunker = SentenceChunker { sentence ->
                    synthesizeAndPlaySentence(sentence, detectedLang)
                }

                groqClient.streamResponse(
                    userMessage = transcript,
                    detectedLanguage = detectedLang,
                    conversationHistory = conversationHistory,
                    onToken = { token ->
                        // Mark LLM first token directly on the first token received
                        latencyTracker.markLlmFirstToken()
                        sentenceChunker.addToken(token)
                    },
                    onComplete = { fullResponse ->
                        // Thread safety: state updates dispatched to coroutine context
                        coroutineScope.launch {
                            sentenceChunker.finish()
                            val finalMetrics = latencyTracker.buildMetrics()
                            _currentMetrics.value = finalMetrics

                            val agentMsg = ChatMessage(
                                sender = MessageSender.AGENT,
                                text = fullResponse,
                                language = detectedLang,
                                latency = finalMetrics
                            )
                            _messages.value = _messages.value + agentMsg
                            _agentState.value = AgentState.IDLE
                            isProcessing = false
                            audioCapture.resumeListening()  // Resume VAD for next utterance
                        }
                    },
                    onError = { error ->
                        Log.e(TAG, "LLM streaming error", error)
                        // Thread safety: state updates dispatched to coroutine context
                        coroutineScope.launch {
                            _agentState.value = AgentState.IDLE
                            isProcessing = false
                            audioCapture.resumeListening()  // Resume VAD on error
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline processing error", e)
                coroutineScope.launch {
                    _agentState.value = AgentState.IDLE
                    isProcessing = false
                    audioCapture.resumeListening()
                }
            }
        }
    }

    private fun synthesizeAndPlaySentence(sentence: String, language: SpokenLanguage) {
        latencyTracker.markTtsStart()
        coroutineScope.launch {
            _agentState.value = AgentState.SPEAKING
        }

        val generatedAudio = piperSynthesizer.synthesize(sentence, language)
        if (generatedAudio != null) {
            latencyTracker.markTtsFirstAudio()
            coroutineScope.launch {
                _currentMetrics.value = latencyTracker.buildMetrics()
            }
            audioPlayback.playAudio(generatedAudio.samples, generatedAudio.sampleRate)
        }
    }

    fun release() {
        stop()
        audioCapture.release()
        audioPlayback.release()
        whisperRecognizer.release()
        piperSynthesizer.release()
    }
}
