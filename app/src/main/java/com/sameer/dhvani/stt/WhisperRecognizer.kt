package com.sameer.dhvani.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class WhisperRecognizer(private val context: Context) {
    companion object {
        private const val TAG = "WhisperRecognizer"
    }

    private var recognizer: OfflineRecognizer? = null

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val whisperConfig = OfflineWhisperModelConfig(
                encoder = "whisper-tiny/tiny-encoder.int8.onnx",
                decoder = "whisper-tiny/tiny-decoder.int8.onnx",
                language = "", // Auto detect language (supports English, Hindi, etc.)
                task = "transcribe",
                tailPaddings = -1
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = "whisper-tiny/tiny-tokens.txt",
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "whisper"
            )

            val featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            )

            val config = OfflineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                hr = HomophoneReplacerConfig(),
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )

            recognizer = OfflineRecognizer(context.assets, config)
            Log.d(TAG, "Sherpa-ONNX Whisper Tiny recognizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper recognizer", e)
        }
    }

    /**
     * Transcribes raw audio float samples (16kHz mono).
     * Returns Pair<transcript, detectedLang>.
     */
    fun transcribe(samples: FloatArray): Pair<String, String> {
        val rec = recognizer ?: return Pair("", "")
        return try {
            val stream = rec.createStream()
            stream.acceptWaveform(samples, 16000)
            rec.decode(stream)
            val result = rec.getResult(stream)
            val text = result.text.trim()
            val lang = result.lang
            stream.release()
            Pair(text, lang)
        } catch (e: Exception) {
            Log.e(TAG, "Speech recognition error", e)
            Pair("", "")
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }
}
