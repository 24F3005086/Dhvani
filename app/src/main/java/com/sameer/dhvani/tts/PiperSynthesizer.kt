package com.sameer.dhvani.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.sameer.dhvani.llm.GroqClient
import com.sameer.dhvani.utils.SpokenLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PiperSynthesizer(private val context: Context) {
    companion object {
        private const val TAG = "PiperSynthesizer"

        /**
         * Common Hinglish words mapping for fallback when text is in Latin script.
         */
        private val COMMON_HINGLISH_WORDS = mapOf(
            "namaste" to "नमस्ते", "namaskar" to "नमस्कार",
            "haan" to "हाँ", "ha" to "हाँ", "nahi" to "नहीं", "nahin" to "नहीं",
            "hai" to "है", "hain" to "हैं", "ka" to "का", "ki" to "की", "ke" to "के",
            "ko" to "को", "se" to "से", "me" to "में", "mein" to "में", "par" to "पर",
            "aur" to "और", "ya" to "या", "kya" to "क्या", "kab" to "कब", "kahan" to "कहाँ",
            "kaise" to "कैसे", "mujhe" to "मुझे", "mera" to "मेरा", "meri" to "मेरी",
            "mere" to "मेरे", "aap" to "आप", "aapka" to "आपका", "aapki" to "आपकी",
            "aapke" to "आपके", "tum" to "तुम", "aaj" to "आज", "kal" to "कल",
            "subah" to "सुबह", "shaam" to "शाम", "raat" to "रात", "baje" to "बजे",
            "karo" to "करो", "karna" to "करना", "batao" to "बताओ", "bolo" to "बोलो",
            "set" to "सेट", "alarm" to "अलार्म", "meeting" to "मीटिंग", "schedule" to "शेड्यूल",
            "weather" to "मौसम", "reminder" to "रिमाइंडर", "bilkul" to "बिल्कुल",
            "theek" to "ठीक", "thik" to "ठीक", "accha" to "अच्छा", "achha" to "अच्छा",
            "dhanyavaad" to "धन्यवाद", "shukriya" to "शुक्रिया", "gaya" to "गया",
            "gayi" to "गयी", "diya" to "दिया", "liye" to "लिए", "next" to "नेक्स्ट"
        )

        fun hasDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

        fun cleanTextForSpeech(text: String): String {
            return GroqClient.stripThinkingTags(text)
                .replace(Regex("[*#_`~><\\[\\]{}|\\\\]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun fallbackRomanToDevanagari(text: String): String {
            val words = text.split(" ")
            return words.joinToString(" ") { word ->
                val cleanWord = word.lowercase().trim { !it.isLetterOrDigit() }
                val dev = COMMON_HINGLISH_WORDS[cleanWord]
                if (dev != null) {
                    val lead = word.takeWhile { !it.isLetterOrDigit() }
                    val trail = word.takeLastWhile { !it.isLetterOrDigit() }
                    "$lead$dev$trail"
                } else {
                    word
                }
            }
        }
    }

    private var hindiTts: OfflineTts? = null
    private var englishTts: OfflineTts? = null

    init {
        initModels()
    }

    private fun initModels() {
        try {
            val espeakDir = prepareEspeakData(context)
            Log.d(TAG, "Prepared espeak-ng-data at: $espeakDir")

            // 1. Hindi Voice (hi_IN-rohan-medium) — native Devanagari Hindi synthesizer
            val hindiVits = OfflineTtsVitsModelConfig(
                model = "tts-hindi/hi_IN-rohan-medium.onnx",
                tokens = "tts-hindi/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.5f,
                noiseScaleW = 0.7f,
                lengthScale = 1.0f
            )
            val hindiModelConfig = OfflineTtsModelConfig(
                vits = hindiVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val hindiConfig = OfflineTtsConfig(model = hindiModelConfig)
            hindiTts = OfflineTts(context.assets, hindiConfig)
            Log.d(TAG, "Piper Hindi TTS (Rohan) initialized successfully")

            // 2. English Voice (en_US-amy-medium) — native English synthesizer
            val englishVits = OfflineTtsVitsModelConfig(
                model = "tts-english/en_US-amy-medium.onnx",
                tokens = "tts-english/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.5f,
                noiseScaleW = 0.7f,
                lengthScale = 1.0f
            )
            val englishModelConfig = OfflineTtsModelConfig(
                vits = englishVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val englishConfig = OfflineTtsConfig(model = englishModelConfig)
            englishTts = OfflineTts(context.assets, englishConfig)
            Log.d(TAG, "Piper English TTS (Amy) initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Piper TTS models", e)
        }
    }

    private fun prepareEspeakData(context: Context): String {
        val targetDir = File(context.filesDir, "espeak-ng-data")
        if (!targetDir.exists() || (targetDir.list()?.isEmpty() == true)) {
            Log.i(TAG, "Copying espeak-ng-data from assets to ${targetDir.absolutePath}")
            targetDir.mkdirs()
            copyAssetFolder(context, "espeak-ng-data", context.filesDir)
        }
        return targetDir.absolutePath
    }

    private fun copyAssetFolder(context: Context, srcName: String, dstDir: File) {
        val assetList = context.assets.list(srcName) ?: return
        val destFile = File(dstDir, srcName)

        if (assetList.isEmpty()) {
            destFile.parentFile?.mkdirs()
            try {
                context.assets.open(srcName).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed copying file $srcName", e)
            }
        } else {
            destFile.mkdirs()
            for (subName in assetList) {
                copyAssetFolder(context, "$srcName/$subName", dstDir)
            }
        }
    }

    /**
     * Synthesizes speech with dedicated native routing:
     * - Devanagari text (Hinglish/Hindi) -> Hindi Voice (hi_IN-rohan-medium).
     *   Produces authentic, natural Indian Hindi pronunciation.
     * - English/Latin text -> English Voice (en_US-amy-medium).
     *   Produces clear, fluent English pronunciation.
     */
    fun synthesize(rawText: String, detectedLanguage: SpokenLanguage): GeneratedAudio? {
        val text = cleanTextForSpeech(rawText)
        if (text.isBlank()) return null

        return try {
            val isDevanagari = hasDevanagari(text)
            val isHinglishOrHindi = detectedLanguage == SpokenLanguage.HINGLISH || detectedLanguage == SpokenLanguage.HINDI

            if (isDevanagari || isHinglishOrHindi) {
                // Hinglish/Hindi routing: prefer Devanagari with Hindi Rohan voice
                val textToSpeak = if (isDevanagari) {
                    text
                } else {
                    fallbackRomanToDevanagari(text)
                }

                Log.d(TAG, "Speaking with Hindi Voice (Rohan): '$textToSpeak'")
                val audio = hindiTts?.generate(textToSpeak, sid = 0, speed = 1.0f)
                if (audio != null) {
                    audio
                } else {
                    Log.w(TAG, "Hindi TTS failed, falling back to English voice")
                    englishTts?.generate(text, sid = 0, speed = 1.0f)
                }
            } else {
                // Pure English routing: use English Amy voice
                Log.d(TAG, "Speaking with English Voice (Amy): '$text'")
                val audio = englishTts?.generate(text, sid = 0, speed = 1.0f)
                if (audio != null) {
                    audio
                } else {
                    Log.w(TAG, "English TTS failed, falling back to Hindi voice")
                    hindiTts?.generate(text, sid = 0, speed = 1.0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed for text: '$text'", e)
            null
        }
    }

    fun release() {
        try {
            hindiTts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Hindi TTS", e)
        }
        hindiTts = null

        try {
            englishTts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing English TTS", e)
        }
        englishTts = null
    }
}
