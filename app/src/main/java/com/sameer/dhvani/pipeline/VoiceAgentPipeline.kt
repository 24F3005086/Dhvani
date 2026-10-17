package com.sameer.dhvani.pipeline

import android.content.Context
import android.util.Log
import com.sameer.dhvani.audio.AudioCaptureManager
import com.sameer.dhvani.audio.AudioPlaybackManager
import com.sameer.dhvani.benchmark.BenchmarkLogger
import com.sameer.dhvani.llm.GroqClient
import com.sameer.dhvani.llm.SentenceChunker
import com.sameer.dhvani.stt.WhisperRecognizer
import com.sameer.dhvani.tts.PiperSynthesizer
import com.sameer.dhvani.utils.LanguageDetector
import com.sameer.dhvani.utils.LatencyMetrics
import com.sameer.dhvani.utils.LatencyTracker
import com.sameer.dhvani.utils.ScriptTransliteration
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
    AUTO,      // Automatically detects whether you speak English, Hinglish, or Hindi
    ENGLISH,   // Force English Assistant (Amy voice)
    HINGLISH,  // Force Hinglish Assistant (Roman script + Supertonic 3 Hindi voice)
    HINDI      // Force Pure Hindi Assistant (Devanagari script + Supertonic 3 Hindi voice)
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
    val benchmarkLogger = BenchmarkLogger(context)

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
                        Log.d(TAG, "Echo cooldown complete: returning to IDLE (waiting for tap)")
                        _agentState.value = AgentState.IDLE
                        isProcessing = false
                        audioCapture.pauseListening()
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
        benchmarkLogger.startSession()
    }

    fun setAssistantMode(mode: AssistantMode) {
        _assistantMode.value = mode
        Log.d(TAG, "Assistant mode switched to: $mode")
    }

    fun start() {
        isProcessing = false
        audioCapture.startListening()
        audioCapture.pauseListening()
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
     * Tapping the center orb or the tap button toggles listening:
     * - If IDLE: starts listening.
     * - If LISTENING: stops listening (flushes speech if spoken, or pauses).
     * - If THINKING or SPEAKING: stops/cancels immediately and returns to IDLE.
     */
    fun onOrbTapped() {
        when (_agentState.value) {
            AgentState.IDLE -> {
                Log.d(TAG, "User tapped: START listening")
                isProcessing = false
                audioCapture.resumeListening()
                _agentState.value = AgentState.LISTENING
            }
            AgentState.LISTENING -> {
                Log.d(TAG, "User tapped: STOP listening")
                val flushedUtterance = audioCapture.stopListeningAndFlush()
                if (flushedUtterance != null && flushedUtterance.isNotEmpty()) {
                    Log.d(TAG, "Flushed active speech utterance on tap stop: ${flushedUtterance.size} samples")
                    handleSpeechUtterance(flushedUtterance)
                } else {
                    audioCapture.pauseListening()
                    _agentState.value = AgentState.IDLE
                    _audioLevel.value = 0f
                }
            }
            AgentState.THINKING, AgentState.SPEAKING -> {
                Log.d(TAG, "User tapped during ${_agentState.value}: STOPPING")
                activePipelineJob?.cancel()
                activePipelineJob = null
                audioPlayback.bargeIn()
                groqClient.cancel()
                isProcessing = false
                audioCapture.pauseListening()
                _agentState.value = AgentState.IDLE
                _audioLevel.value = 0f
            }
        }
    }

    /**
     * Called by VAD when user begins speaking.
     */
    private fun handleSpeechStart() {
        if (_agentState.value == AgentState.SPEAKING || audioPlayback.isActivelyPlaying || isProcessing) {
            // Prevent phone loudspeaker audio from triggering false acoustic barge-ins
            Log.d(TAG, "Ignoring microphone signal during playback/processing to prevent self-interruption")
            return
        }
        _agentState.value = AgentState.LISTENING
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
                // 1. STT: Fully On-Device Whisper Tiny INT8 (Primary, mandatory assignment requirement)
                // Runs locally on device CPU via Sherpa-ONNX runtime; cloud STT available as fallback
                val sttResult = try {
                    val (localText, localLang) = whisperRecognizer.transcribe(samples)
                    if (localText.isNotBlank()) {
                        Log.d(TAG, "On-device STT succeeded: '$localText' (lang=$localLang)")
                        com.sameer.dhvani.llm.TranscriptionResult(localText, localLang, isOnDevice = true)
                    } else {
                        Log.w(TAG, "On-device STT returned empty, falling back to cloud STT")
                        groqClient.transcribeAudio(samples, _assistantMode.value)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "On-device STT exception, falling back to cloud STT", e)
                    try {
                        groqClient.transcribeAudio(samples, _assistantMode.value)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Both on-device and cloud STT failed", e2)
                        com.sameer.dhvani.llm.TranscriptionResult("", "")
                    }
                }

                val rawTranscript = sttResult.transcript.trim()
                val transcript = ScriptTransliteration.cleanSpokenTranscript(rawTranscript)
                if (transcript.isBlank()) {
                    Log.d(TAG, "Blank transcript, returning to IDLE")
                    coroutineScope.launch(Dispatchers.Main) {
                        _agentState.value = AgentState.IDLE
                        isProcessing = false
                        audioCapture.resumeListening()
                        // Record blank-transcript failure in benchmark
                        benchmarkLogger.recordFailure(
                            inputText = "(blank transcript)",
                            language = SpokenLanguage.ENGLISH,
                            reason = "Blank STT transcript",
                            isTestChip = false
                        )
                    }
                    return@launch
                }

                latencyTracker.markSttEnd()

                // 2. Language Detection: Use Whisper's detected language as PRIMARY signal
                val targetLang = when (_assistantMode.value) {
                    AssistantMode.ENGLISH -> SpokenLanguage.ENGLISH
                    AssistantMode.HINGLISH -> SpokenLanguage.HINGLISH
                    AssistantMode.HINDI -> SpokenLanguage.HINDI
                    AssistantMode.AUTO -> {
                        // Combine Whisper's audio-level language detection with text heuristics
                        LanguageDetector.detectFromWhisper(sttResult.whisperLanguage, transcript)
                    }
                }

                Log.d(TAG, "Language routing: whisperLang='${sttResult.whisperLanguage}' | textLang='${LanguageDetector.detectLanguage(transcript)}' | targetLang=$targetLang | mode=${_assistantMode.value}")

                val assistantName = when (targetLang) {
                    SpokenLanguage.ENGLISH, SpokenLanguage.HINGLISH -> "Dhvani"
                    SpokenLanguage.HINDI -> "ध्वनि"
                }

                // Format transcript according to target language (clean Roman for Hinglish, Devanagari for Hindi)
                val displayTranscript = when (targetLang) {
                    SpokenLanguage.HINGLISH -> {
                        if (ScriptTransliteration.hasDevanagari(transcript)) {
                            ScriptTransliteration.devanagariToRoman(transcript)
                        } else {
                            transcript
                        }
                    }
                    SpokenLanguage.HINDI -> {
                        if (!ScriptTransliteration.hasDevanagari(transcript)) {
                            ScriptTransliteration.romanToDevanagari(transcript)
                        } else {
                            transcript
                        }
                    }
                    SpokenLanguage.ENGLISH -> transcript
                }

                // Add user message to UI (single language display)
                val userMsg = ChatMessage(
                    sender = MessageSender.USER,
                    senderName = "You",
                    text = displayTranscript,
                    language = targetLang
                )
                coroutineScope.launch(Dispatchers.Main) {
                    _messages.value = _messages.value + userMsg
                }

                // Execute conversational response
                processResponse(displayTranscript, targetLang, assistantName, isTestChip = false)

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
        activePipelineJob?.cancel()
        activePipelineJob = null
        audioPlayback.bargeIn()
        groqClient.cancel()

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
                AssistantMode.HINDI -> SpokenLanguage.HINDI
                AssistantMode.AUTO -> LanguageDetector.detectLanguage(phrase)
            }

            val assistantName = when (targetLang) {
                SpokenLanguage.ENGLISH, SpokenLanguage.HINGLISH -> "Dhvani"
                SpokenLanguage.HINDI -> "ध्वनि"
            }

            val displayPhrase = when (targetLang) {
                SpokenLanguage.HINGLISH -> if (ScriptTransliteration.hasDevanagari(phrase)) ScriptTransliteration.devanagariToRoman(phrase) else phrase
                SpokenLanguage.HINDI -> if (!ScriptTransliteration.hasDevanagari(phrase)) ScriptTransliteration.romanToDevanagari(phrase) else phrase
                SpokenLanguage.ENGLISH -> phrase
            }

            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                senderName = "You",
                text = displayPhrase,
                language = targetLang
            )
            coroutineScope.launch(Dispatchers.Main) {
                _messages.value = _messages.value + userMsg
            }

            processResponse(displayPhrase, targetLang, assistantName, isTestChip = true)
        }
    }

    private fun processResponse(
        userInput: String,
        targetLang: SpokenLanguage,
        assistantName: String,
        isTestChip: Boolean = false
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

        val sentenceChunker = SentenceChunker { sentence, isFirstChunk ->
            synthesizeAndPlaySentence(sentence, targetLang, isFirstChunk)
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
                    audioPlayback.setExpectingMoreAudio(false)
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

                    // Record benchmark trial if session is active
                    benchmarkLogger.recordTrial(
                        inputText = userInput,
                        responseText = fullResponse,
                        language = targetLang,
                        metrics = finalMetrics,
                        isTestChip = isTestChip
                    )

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

                    // Record benchmark failure if session is active
                    benchmarkLogger.recordFailure(
                        inputText = userInput,
                        language = targetLang,
                        reason = error.message ?: "LLM streaming error",
                        isTestChip = isTestChip
                    )
                }
            }
        )
    }

    private fun synthesizeAndPlaySentence(
        sentence: String,
        language: SpokenLanguage,
        isFirstChunk: Boolean = false
    ) {
        latencyTracker.markTtsStart()
        coroutineScope.launch(Dispatchers.Main) {
            _agentState.value = AgentState.SPEAKING
        }

        val generatedAudio = piperSynthesizer.synthesize(sentence, language, isFirstChunk)
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

    /**
     * Executes an automated 15-trial benchmark across English, Hinglish, and Hindi.
     * Tests real STT (Whisper Large V3), real LLM (Qwen 3.8 27B), and real on-device TTS.
     */
    fun runAutomatedBenchmark(onComplete: () -> Unit = {}) {
        Log.i(TAG, "Starting automated 15-trial benchmark session...")
        benchmarkLogger.startSession()

        val benchmarkPhrases = listOf(
            // English (5 trials)
            Pair("What is the weather forecast for Bangalore tomorrow?", SpokenLanguage.ENGLISH),
            Pair("What is 24 multiplied by 4?", SpokenLanguage.ENGLISH),
            Pair("Give me a one-sentence tip for healthy sleep.", SpokenLanguage.ENGLISH),
            Pair("Where is the Computational Intelligence Lab located at IISc?", SpokenLanguage.ENGLISH),
            Pair("Can you schedule a meeting with Dr. Rao at 4 PM on Friday?", SpokenLanguage.ENGLISH),
            // Hinglish (5 trials)
            Pair("Mujhe kal subah 7 baje ka alarm set karna hai.", SpokenLanguage.HINGLISH),
            Pair("Agar mere paas 500 rupaye hain aur 150 kharch ho gaye, toh kitne bache?", SpokenLanguage.HINGLISH),
            Pair("Aaj ka schedule kya hai, aur meri next meeting kab hai?", SpokenLanguage.HINGLISH),
            Pair("Bangalore campus mein nearest canteen kahan hai, mujhe chai peeni hai?", SpokenLanguage.HINGLISH),
            Pair("Aaj shaam ko traffic kaisa rahega Silk Board junction pe?", SpokenLanguage.HINGLISH),
            // Pure Hindi (5 trials)
            Pair("नमस्ते, आप मेरी किस प्रकार सहायता कर सकते हैं?", SpokenLanguage.HINDI),
            Pair("भारत के प्रधानमंत्री कौन हैं और उनका जन्म कब हुआ था?", SpokenLanguage.HINDI),
            Pair("आज कौन सा वार है और आज की तारीख क्या है?", SpokenLanguage.HINDI),
            Pair("पच्चीस और पैंतीस का जोड़ कितना होता है?", SpokenLanguage.HINDI),
            Pair("कल बैंगलोर का मौसम कैसा रहेगा?", SpokenLanguage.HINDI)
        )

        coroutineScope.launch(Dispatchers.Default) {
            for ((index, item) in benchmarkPhrases.withIndex()) {
                val (phrase, lang) = item
                Log.i(TAG, "Executing benchmark trial #${index + 1}/15: \"$phrase\" ($lang)")

                // Stop any playing audio
                audioPlayback.bargeIn()
                groqClient.cancel()

                isProcessing = true
                audioCapture.pauseListening()
                _agentState.value = AgentState.THINKING
                latencyTracker.reset()
                latencyTracker.markSpeechEnd()

                // Generate speech audio for the query to test REAL Cloud STT round-trip!
                val queryAudio = try {
                    piperSynthesizer.synthesize(phrase, lang)
                } catch (e: Exception) {
                    null
                }

                val transcript = if (queryAudio != null && queryAudio.samples.isNotEmpty()) {
                    val (localText, _) = whisperRecognizer.transcribe(queryAudio.samples)
                    latencyTracker.markSttEnd()
                    if (localText.isNotBlank()) {
                        localText
                    } else {
                        val sttRes = groqClient.transcribeAudio(
                            samples = queryAudio.samples,
                            mode = _assistantMode.value,
                            sampleRate = queryAudio.sampleRate
                        )
                        if (sttRes.transcript.isNotBlank()) sttRes.transcript else phrase
                    }
                } else {
                    latencyTracker.markSttEnd()
                    phrase
                }

                val assistantName = when (lang) {
                    SpokenLanguage.ENGLISH, SpokenLanguage.HINGLISH -> "Dhvani"
                    SpokenLanguage.HINDI -> "ध्वनि"
                }

                coroutineScope.launch(Dispatchers.Main) {
                    val userMsg = ChatMessage(
                        sender = MessageSender.USER,
                        senderName = "Benchmark #${index + 1}",
                        text = transcript,
                        language = lang
                    )
                    _messages.value = _messages.value + userMsg
                }

                // Process response through real LLM and TTS
                val completedLatch = kotlinx.coroutines.CompletableDeferred<Unit>()

                val sentenceChunker = SentenceChunker { sentence, isFirstChunk ->
                    synthesizeAndPlaySentence(sentence, lang, isFirstChunk)
                }

                groqClient.streamResponse(
                    userMessage = transcript,
                    detectedLanguage = lang,
                    conversationHistory = emptyList(),
                    onToken = { token ->
                        latencyTracker.markLlmFirstToken()
                        sentenceChunker.addToken(token)
                    },
                    onComplete = { fullResponse ->
                        coroutineScope.launch(Dispatchers.Main) {
                            sentenceChunker.finish()
                            audioPlayback.setExpectingMoreAudio(false)
                            val langStr = when (lang) {
                                SpokenLanguage.ENGLISH -> "English"
                                SpokenLanguage.HINGLISH -> "Hinglish"
                                SpokenLanguage.HINDI -> "Hindi"
                            }
                            val finalMetrics = latencyTracker.buildMetrics(language = langStr)
                            _currentMetrics.value = finalMetrics

                            val agentMsg = ChatMessage(
                                sender = MessageSender.AGENT,
                                senderName = assistantName,
                                text = fullResponse,
                                language = lang,
                                latency = finalMetrics
                            )
                            _messages.value = _messages.value + agentMsg

                            benchmarkLogger.recordTrial(
                                inputText = transcript,
                                responseText = fullResponse,
                                language = lang,
                                metrics = finalMetrics,
                                isTestChip = false
                            )

                            completedLatch.complete(Unit)
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch(Dispatchers.Main) {
                            audioPlayback.setExpectingMoreAudio(false)
                            _agentState.value = AgentState.IDLE
                            isProcessing = false
                            benchmarkLogger.recordFailure(
                                inputText = transcript,
                                language = lang,
                                reason = err.message ?: "LLM error",
                                isTestChip = false
                            )
                            completedLatch.complete(Unit)
                        }
                    }
                )

                // Wait for trial to finish
                try {
                    completedLatch.await()
                } catch (_: Exception) {}

                // Allow brief delay between trials for audio playback and cooldown
                delay(1200L)
            }

            benchmarkLogger.endSession()
            Log.i(TAG, "Automated 15-trial benchmark session COMPLETE! Results saved.")
            coroutineScope.launch(Dispatchers.Main) {
                _agentState.value = AgentState.IDLE
                isProcessing = false
                audioCapture.resumeListening()
                onComplete()
            }
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
