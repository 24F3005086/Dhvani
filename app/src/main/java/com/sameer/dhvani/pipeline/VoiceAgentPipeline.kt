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
import kotlinx.coroutines.delay
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

enum class AssistantMode {
    AUTO,      // Automatically detects whether you speak English or Hinglish
    ENGLISH,   // Force English Assistant (Amy voice)
    HINGLISH   // Force Hinglish Assistant (Rohan voice)
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val senderName: String = "",
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
        private const val ECHO_COOLDOWN_MS = 350L
    }

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _assistantMode = MutableStateFlow(AssistantMode.AUTO)
    val assistantMode: StateFlow<AssistantMode> = _assistantMode.asStateFlow()

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
    private val audioPlayback: AudioPlaybackManager
    private val whisperRecognizer = WhisperRecognizer(context)
    private val piperSynthesizer = PiperSynthesizer(context)
    private val groqClient = GroqClient()
    private val latencyTracker = LatencyTracker()

    init {
        audioPlayback = AudioPlaybackManager(
            onPlaybackStarted = {
                Log.d(TAG, "AudioPlayback started: ensuring VAD is paused")
                audioCapture.pauseListening()
                _agentState.value = AgentState.SPEAKING
            },
            onPlaybackFinished = {
                Log.d(TAG, "AudioPlayback finished: starting echo cooldown")
                coroutineScope.launch(Dispatchers.Main) {
                    // Critical: wait for acoustic reverberations in the room to fade
                    delay(ECHO_COOLDOWN_MS)
                    if (!audioPlayback.isActivelyPlaying && _agentState.value == AgentState.SPEAKING) {
                        Log.d(TAG, "Echo cooldown complete: resuming listening")
                        _agentState.value = AgentState.IDLE
                        isProcessing = false
                        audioCapture.resumeListening()
                    }
                }
            }
        )

        audioCapture = AudioCaptureManager(
            context = context,
            onSpeechStart = { handleSpeechStart() },
            onSpeechUtterance = { samples -> handleSpeechUtterance(samples) },
            onAudioLevel = { level -> _audioLevel.value = level }
        )
    }

    fun setAssistantMode(mode: AssistantMode) {
        _assistantMode.value = mode
        Log.d(TAG, "Assistant mode switched to: $mode")
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
     * Tapping the center orb can trigger barge-in or manual listen toggle.
     */
    fun onOrbTapped() {
        if (_agentState.value == AgentState.SPEAKING || audioPlayback.isActivelyPlaying) {
            // Barge-in interruption
            Log.d(TAG, "User tapped Orb during speech: executing barge-in")
            bargeIn()
        } else if (_agentState.value == AgentState.IDLE) {
            Log.d(TAG, "User tapped Orb while IDLE: listening for speech")
            audioCapture.resumeListening()
            _agentState.value = AgentState.LISTENING
        }
    }

    private fun bargeIn() {
        activePipelineJob?.cancel()
        activePipelineJob = null
        audioPlayback.bargeIn()
        groqClient.cancel()
        isProcessing = false
        coroutineScope.launch(Dispatchers.Main) {
            delay(100)
            audioCapture.resumeListening()
            _agentState.value = AgentState.LISTENING
        }
    }

    /**
     * Called by VAD when user begins speaking.
     */
    private fun handleSpeechStart() {
        if (_agentState.value == AgentState.SPEAKING || audioPlayback.isActivelyPlaying) {
            Log.d(TAG, "Speech detected during playback! Initiating barge-in")
            bargeIn()
        } else if (!isProcessing) {
            _agentState.value = AgentState.LISTENING
        }
    }

    /**
     * Called by VAD when silence is detected after user speech.
     * Uses Groq Cloud Whisper Large V3 for STT with language detection.
     */
    private fun handleSpeechUtterance(samples: FloatArray) {
        val currentState = _agentState.value
        // Half-duplex guard: strictly ignore if currently processing or playing
        if (isProcessing || currentState == AgentState.SPEAKING || audioPlayback.isActivelyPlaying) {
            Log.d(TAG, "Ignoring utterance: pipeline busy (state=$currentState, isPlaying=${audioPlayback.isActivelyPlaying})")
            return
        }

        isProcessing = true
        // Pause VAD immediately so microphone will not capture any speaker audio
        audioCapture.pauseListening()
        _agentState.value = AgentState.THINKING
        latencyTracker.reset()
        latencyTracker.markSpeechEnd()

        activePipelineJob?.cancel()
        activePipelineJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                // 1. STT: Groq Cloud Whisper Large V3 (primary) → On-device Whisper Tiny (fallback)
                val sttResult = try {
                    val cloudResult = groqClient.transcribeAudio(samples)
                    if (cloudResult.transcript.isNotBlank()) {
                        cloudResult
                    } else {
                        Log.w(TAG, "Cloud STT returned empty, using on-device Whisper Tiny")
                        val (text, lang) = whisperRecognizer.transcribe(samples)
                        com.sameer.dhvani.llm.TranscriptionResult(text, lang, isOnDevice = true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud STT exception, using on-device Whisper Tiny", e)
                    val (text, lang) = whisperRecognizer.transcribe(samples)
                    com.sameer.dhvani.llm.TranscriptionResult(text, lang, isOnDevice = true)
                }

                val transcript = sttResult.transcript.trim()
                if (transcript.isBlank()) {
                    Log.d(TAG, "Blank transcript, returning to IDLE")
                    coroutineScope.launch(Dispatchers.Main) {
                        _agentState.value = AgentState.IDLE
                        isProcessing = false
                        audioCapture.resumeListening()
                    }
                    return@launch
                }

                latencyTracker.markSttEnd()

                // 2. Language Detection: Use Whisper's detected language as PRIMARY signal
                val targetLang = when (_assistantMode.value) {
                    AssistantMode.ENGLISH -> SpokenLanguage.ENGLISH
                    AssistantMode.HINGLISH -> SpokenLanguage.HINGLISH
                    AssistantMode.AUTO -> {
                        // Combine Whisper's audio-level language detection with text heuristics
                        LanguageDetector.detectFromWhisper(sttResult.whisperLanguage, transcript)
                    }
                }

                Log.d(TAG, "Language routing: whisperLang='${sttResult.whisperLanguage}' | textLang='${LanguageDetector.detectLanguage(transcript)}' | targetLang=$targetLang | mode=${_assistantMode.value}")

                val assistantName = when (targetLang) {
                    SpokenLanguage.ENGLISH -> "Dhvani (Piper Edge)"
                    SpokenLanguage.HINGLISH, SpokenLanguage.HINDI -> "ध्वनि (Male Neural)"
                }

                // Add user message to UI
                val userMsg = ChatMessage(
                    sender = MessageSender.USER,
                    senderName = "You",
                    text = transcript,
                    language = targetLang
                )
                coroutineScope.launch(Dispatchers.Main) {
                    _messages.value = _messages.value + userMsg
                }

                // Execute conversational response
                processResponse(transcript, targetLang, assistantName)

            } catch (e: Exception) {
                Log.e(TAG, "Error in speech utterance processing", e)
                coroutineScope.launch(Dispatchers.Main) {
                    _agentState.value = AgentState.IDLE
                    isProcessing = false
                    audioCapture.resumeListening()
                }
            }
        }
    }

    /**
     * Executes test phrase directly (for 1-tap chip testing during demo/evaluation).
     */
    fun testUtterance(phrase: String) {
        if (isProcessing || audioPlayback.isActivelyPlaying) {
            bargeIn()
        }

        isProcessing = true
        audioCapture.pauseListening()
        _agentState.value = AgentState.THINKING
        latencyTracker.reset()
        latencyTracker.markSpeechEnd()
        latencyTracker.markSttEnd()

        activePipelineJob?.cancel()
        activePipelineJob = coroutineScope.launch(Dispatchers.Default) {
            val targetLang = when (_assistantMode.value) {
                AssistantMode.ENGLISH -> SpokenLanguage.ENGLISH
                AssistantMode.HINGLISH -> SpokenLanguage.HINGLISH
                AssistantMode.AUTO -> LanguageDetector.detectLanguage(phrase)
            }

            val assistantName = when (targetLang) {
                SpokenLanguage.ENGLISH -> "Dhvani (Piper Edge)"
                SpokenLanguage.HINGLISH, SpokenLanguage.HINDI -> "ध्वनि (Male Neural)"
            }

            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                senderName = "You",
                text = phrase,
                language = targetLang
            )
            coroutineScope.launch(Dispatchers.Main) {
                _messages.value = _messages.value + userMsg
            }

            processResponse(phrase, targetLang, assistantName)
        }
    }

    private fun processResponse(
        userInput: String,
        targetLang: SpokenLanguage,
        assistantName: String
    ) {
        // Last 5 messages for multi-turn context
        val conversationHistory = _messages.value.takeLast(5).map { msg ->
            val role = when (msg.sender) {
                MessageSender.USER -> "user"
                MessageSender.AGENT -> "assistant"
            }
            Pair(role, msg.text)
        }

        audioPlayback.setExpectingMoreAudio(true)

        val sentenceChunker = SentenceChunker { sentence ->
            synthesizeAndPlaySentence(sentence, targetLang)
        }

        groqClient.streamResponse(
            userMessage = userInput,
            detectedLanguage = targetLang,
            conversationHistory = conversationHistory,
            onToken = { token ->
                latencyTracker.markLlmFirstToken()
                sentenceChunker.addToken(token)
            },
            onComplete = { fullResponse ->
                coroutineScope.launch(Dispatchers.Main) {
                    sentenceChunker.finish()
                    val langStr = when (targetLang) {
                        SpokenLanguage.ENGLISH -> "English"
                        SpokenLanguage.HINGLISH -> "Hinglish"
                        SpokenLanguage.HINDI -> "Hindi"
                    }
                    val finalMetrics = latencyTracker.buildMetrics(language = langStr, emotion = "Calm (94%)")
                    _currentMetrics.value = finalMetrics

                    val agentMsg = ChatMessage(
                        sender = MessageSender.AGENT,
                        senderName = assistantName,
                        text = fullResponse,
                        language = targetLang,
                        latency = finalMetrics
                    )
                    _messages.value = _messages.value + agentMsg
                    // Note: audioCapture.resumeListening() will be triggered automatically
                    // by audioPlayback.onPlaybackFinished AFTER audio finishes playing + cooldown.
                }
            },
            onError = { error ->
                Log.e(TAG, "Groq LLM streaming error", error)
                coroutineScope.launch(Dispatchers.Main) {
                    audioPlayback.setExpectingMoreAudio(false)
                    _agentState.value = AgentState.IDLE
                    isProcessing = false
                    audioCapture.resumeListening()
                }
            }
        )
    }

    private fun synthesizeAndPlaySentence(sentence: String, language: SpokenLanguage) {
        latencyTracker.markTtsStart()
        coroutineScope.launch(Dispatchers.Main) {
            _agentState.value = AgentState.SPEAKING
        }

        val generatedAudio = piperSynthesizer.synthesize(sentence, language)
        if (generatedAudio != null) {
            latencyTracker.markTtsFirstAudio()
            val langStr = when (language) {
                SpokenLanguage.ENGLISH -> "English"
                SpokenLanguage.HINGLISH -> "Hinglish"
                SpokenLanguage.HINDI -> "Hindi"
            }
            coroutineScope.launch(Dispatchers.Main) {
                _currentMetrics.value = latencyTracker.buildMetrics(language = langStr, emotion = "Calm (94%)")
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
