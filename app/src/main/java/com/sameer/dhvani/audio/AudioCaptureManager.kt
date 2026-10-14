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
    private var vad: Vad? = null
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null

    init {
        initVad()
    }

    private fun initVad() {
        try {
            val sileroConfig = SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
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

            vad = Vad(context.assets, vadModelConfig)
            Log.d(TAG, "Sherpa-ONNX Silero VAD initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Silero VAD", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) return

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
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware NoiseSuppressor enabled")
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware AcousticEchoCanceler enabled")
                }
            }

            audioRecord?.startRecording()
            isRecording = true
            vad?.reset()

            recordingThread = thread(name = "DhvaniAudioRecordThread") {
                val shortBuf = ShortArray(512)
                val floatBuf = FloatArray(512)
                var wasSpeechDetected = false

                while (isRecording) {
                    val read = audioRecord?.read(shortBuf, 0, shortBuf.size) ?: 0
                    if (read > 0) {
                        var sumSquare = 0.0
                        for (i in 0 until read) {
                            val sample = shortBuf[i] / 32768.0f
                            floatBuf[i] = sample
                            sumSquare += (sample * sample)
                        }

                        // Compute audio energy for UI waveform
                        val rms = Math.sqrt(sumSquare / read).toFloat()
                        onAudioLevel(rms)

                        vad?.let { detector ->
                            detector.acceptWaveform(floatBuf.copyOf(read))

                            if (detector.isSpeechDetected()) {
                                if (!wasSpeechDetected) {
                                    wasSpeechDetected = true
                                    onSpeechStart()
                                }
                            }

                            // When speech finishes and silence threshold is reached
                            while (!detector.empty()) {
                                val segment = detector.front()
                                val speechSamples = segment.samples
                                detector.pop()
                                wasSpeechDetected = false

                                if (speechSamples.isNotEmpty()) {
                                    onSpeechUtterance(speechSamples)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
        }
    }

    fun stopListening() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    fun release() {
        stopListening()
        vad?.release()
        vad = null
    }
}
