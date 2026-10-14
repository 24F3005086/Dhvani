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
        audioCapture.startListening()
        _agentState.value = AgentState.IDLE
    }

    fun stop() {
        audioCapture.stopListening()
        audioPlayback.bargeIn()
        groqClient.cancel()
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
        }
        _agentState.value = AgentState.LISTENING
    }

    /**
     * Called by VAD when silence is detected after user speech.
     */
    private fun handleSpeechUtterance(samples: FloatArray) {
        latencyTracker.reset()
        latencyTracker.markSpeechEnd()
        _agentState.value = AgentState.THINKING

        coroutineScope.launch(Dispatchers.Default) {
            // 1. On-Device STT (Whisper Tiny INT8)
            val (transcript, whisperLang) = whisperRecognizer.transcribe(samples)
            if (transcript.isBlank()) {
                _agentState.value = AgentState.IDLE
                return@launch
            }

            latencyTracker.markSttEnd()

            // 2. Language Detection (English vs Hinglish vs Hindi)
            val detectedLang = LanguageDetector.detectLanguage(transcript)

            // Add user message to UI
            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                text = transcript,
                language = detectedLang
            )
            _messages.value = _messages.value + userMsg

            // 3. Cloud LLM with Streaming Sentence-Chunked TTS
            val sentenceChunker = SentenceChunker { sentence ->
                latencyTracker.markLlmFirstToken()
                synthesizeAndPlaySentence(sentence, detectedLang)
            }

            groqClient.streamResponse(
                userMessage = transcript,
                detectedLanguage = detectedLang,
                onToken = { token ->
                    sentenceChunker.addToken(token)
                },
                onComplete = { fullResponse ->
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
                },
                onError = { error ->
                    Log.e(TAG, "LLM streaming error", error)
                    _agentState.value = AgentState.IDLE
                }
            )
        }
    }

    private fun synthesizeAndPlaySentence(sentence: String, language: SpokenLanguage) {
        latencyTracker.markTtsStart()
        _agentState.value = AgentState.SPEAKING

        val generatedAudio = piperSynthesizer.synthesize(sentence, language)
        if (generatedAudio != null) {
            latencyTracker.markTtsFirstAudio()
            _currentMetrics.value = latencyTracker.buildMetrics()
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
