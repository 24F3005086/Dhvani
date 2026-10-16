package com.sameer.dhvani.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlin.concurrent.thread

class AudioCaptureManager(
    private val context: Context,
    private val onSpeechStart: () -> Unit,
    private val onSpeechUtterance: (FloatArray) -> Unit,
    private val onAudioLevel: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var vad: Vad? = null
    private val vadLock = Any()

    @Volatile private var isRecording = false

    @Volatile
    var isPaused: Boolean = false
        private set

    @Volatile private var wasSpeechDetected = false
    private var recordingThread: Thread? = null

    // Ring buffer of recent pre-speech audio (~320ms @ 16kHz)
    // Ensures leading syllables (e.g. "मुझे", "Agar", "150") are never clipped at speech onset.
    private val preSpeechRingBuffer = java.util.ArrayDeque<FloatArray>(12)
    private var preRollForCurrentUtterance: FloatArray? = null

    init {
        initVad()
    }

    private fun initVad() {
        try {
            val sileroConfig = SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                threshold = 0.35f,
                minSilenceDuration = 0.65f,
                minSpeechDuration = 0.20f,
                windowSize = 512,
                maxSpeechDuration = 30.0f
            )

            val vadModelConfig = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                tenVadModelConfig = TenVadModelConfig(),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )

            synchronized(vadLock) {
                vad = Vad(context.assets, vadModelConfig)
            }
            Log.d(TAG, "Sherpa-ONNX Silero VAD initialized successfully (threshold: 0.35, minSilence: 0.65s, pre-speech ring buffer enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Silero VAD", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) {
            Log.d(TAG, "AudioRecord is already running")
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufSize, SAMPLE_RATE * 2)

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware NoiseSuppressor enabled")
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware AcousticEchoCanceler enabled")
                }
            }

            audioRecord?.startRecording()
            isRecording = true
            isPaused = false
            wasSpeechDetected = false

            synchronized(vadLock) {
                vad?.reset()
            }

            recordingThread = thread(name = "DhvaniAudioRecordThread") {
                val shortBuf = ShortArray(512)
                val floatBuf = FloatArray(512)

                while (isRecording) {
                    val record = audioRecord ?: break
                    val read = try {
                        record.read(shortBuf, 0, shortBuf.size)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading from AudioRecord", e)
                        -1
                    }

                    if (!isRecording) break

                    if (read <= 0) {
                        if (read < 0) {
                            Log.w(TAG, "AudioRecord read returned error code: $read")
                            try {
                                Thread.sleep(10)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }
                        continue
                    }

                    // Keep buffering audio continuously from AudioRecord to prevent glitches
                    // and buffer overflows, but skip VAD processing while paused.
                    if (isPaused) {
                        onAudioLevel(0f)
                        continue
                    }

                    var sumSquare = 0.0
                    for (i in 0 until read) {
                        val sample = shortBuf[i] / 32768.0f
                        floatBuf[i] = sample
                        sumSquare += (sample * sample)
                    }

                    // Compute audio energy for UI waveform
                    val rms = Math.sqrt(sumSquare / read).toFloat()
                    onAudioLevel(rms)

                    val currentChunk = floatBuf.copyOf(read)
                    var speechStarted = false
                    val speechUtterances = mutableListOf<FloatArray>()

                    synchronized(vadLock) {
                        if (isPaused) return@synchronized

                        vad?.let { detector ->
                            detector.acceptWaveform(currentChunk)

                            if (detector.isSpeechDetected()) {
                                if (!wasSpeechDetected) {
                                    wasSpeechDetected = true
                                    speechStarted = true
                                    // Capture the pre-speech audio frames accumulated right before speech trigger
                                    val preRollTotal = preSpeechRingBuffer.sumOf { it.size }
                                    if (preRollTotal > 0) {
                                        val combinedPreRoll = FloatArray(preRollTotal)
                                        var off = 0
                                        for (chunk in preSpeechRingBuffer) {
                                            System.arraycopy(chunk, 0, combinedPreRoll, off, chunk.size)
                                            off += chunk.size
                                        }
                                        preRollForCurrentUtterance = combinedPreRoll
                                    }
                                }
                            } else {
                                // While no speech is detected, buffer incoming frames in a sliding window (~320ms)
                                preSpeechRingBuffer.addLast(currentChunk)
                                if (preSpeechRingBuffer.size > 10) {
                                    preSpeechRingBuffer.removeFirst()
                                }
                            }

                            // When speech finishes and silence threshold is reached
                            while (!detector.empty()) {
                                val segment = detector.front()
                                val speechSamples = segment.samples
                                detector.pop()
                                wasSpeechDetected = false

                                if (speechSamples.isNotEmpty()) {
                                    val preRoll = preRollForCurrentUtterance
                                    val fullUtterance = if (preRoll != null && preRoll.isNotEmpty()) {
                                        val merged = FloatArray(preRoll.size + speechSamples.size)
                                        System.arraycopy(preRoll, 0, merged, 0, preRoll.size)
                                        System.arraycopy(speechSamples, 0, merged, preRoll.size, speechSamples.size)
                                        preRollForCurrentUtterance = null
                                        merged
                                    } else {
                                        speechSamples
                                    }
                                    speechUtterances.add(fullUtterance)
                                }
                                preSpeechRingBuffer.clear()
                            }
                        }
                    }

                    if (speechStarted) {
                        onSpeechStart()
                    }

                    for (utterance in speechUtterances) {
                        onSpeechUtterance(utterance)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
        }
    }

    /**
     * Pauses VAD processing while keeping AudioRecord active in the background.
     * Prevents duplicate responses and buffer glitches during THINKING/SPEAKING states.
     */
    fun pauseListening() {
        Log.d(TAG, "Pausing audio listening / VAD processing")
        isPaused = true
        synchronized(vadLock) {
            vad?.reset()
            wasSpeechDetected = false
            preSpeechRingBuffer.clear()
            preRollForCurrentUtterance = null
        }
    }

    /**
     * Resumes VAD processing after THINKING/SPEAKING completes.
     * Resets VAD state to ensure clean detection of new speech.
     */
    fun resumeListening() {
        Log.d(TAG, "Resuming audio listening / VAD processing")
        synchronized(vadLock) {
            vad?.reset()
            wasSpeechDetected = false
            isPaused = false
            preSpeechRingBuffer.clear()
            preRollForCurrentUtterance = null
        }
    }

    fun stopListening() {
        isRecording = false
        isPaused = false
        wasSpeechDetected = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        try {
            noiseSuppressor?.release()
            echoCanceler?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
        noiseSuppressor = null
        echoCanceler = null
        recordingThread = null
    }

    fun release() {
        stopListening()
        synchronized(vadLock) {
            vad?.release()
            vad = null
        }
    }
}
