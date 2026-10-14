package com.sameer.dhvani.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.sameer.dhvani.utils.SpokenLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PiperSynthesizer(private val context: Context) {
    companion object {
        private const val TAG = "PiperSynthesizer"

        /**
         * Comprehensive Roman/Latin to Devanagari transliteration map.
         * Covers consonant clusters, vowels, and common Hinglish patterns.
         */
        private val TRANSLITERATION_MAP = linkedMapOf(
            // Consonant clusters (must come BEFORE single consonants)
            "ksh" to "क्ष", "gya" to "ज्ञ", "tra" to "त्र", "shr" to "श्र",
            "shh" to "षः", "chh" to "छ", "dha" to "ध", "bha" to "भ",
            "tha" to "थ", "sha" to "श", "kha" to "ख", "gha" to "घ",
            "cha" to "च", "jha" to "झ", "pha" to "फ", "nha" to "ण",
            "thi" to "थी", "dhi" to "धी",
            // Common double consonants
            "sh" to "श", "ch" to "च", "th" to "थ", "dh" to "ध",
            "bh" to "भ", "ph" to "फ", "kh" to "ख", "gh" to "घ",
            "jh" to "झ", "ng" to "ं",
            // Vowels (longer patterns first)
            "aa" to "आ", "ee" to "ई", "oo" to "ऊ", "ai" to "ऐ",
            "au" to "औ", "ou" to "औ", "ei" to "ऐ",
            // Single consonants
            "k" to "क", "g" to "ग", "c" to "क", "j" to "ज",
            "t" to "त", "d" to "द", "n" to "न", "p" to "प",
            "b" to "ब", "m" to "म", "y" to "य", "r" to "र",
            "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
            "h" to "ह", "f" to "फ", "z" to "ज़", "q" to "क़",
            "x" to "क्स",
            // Single vowels
            "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए",
            "o" to "ओ"
        )

        /**
         * Common Hinglish words with correct Devanagari spellings.
         * These override the character-by-character transliteration for accuracy.
         */
        private val WORD_MAP = mapOf(
            // Greetings
            "namaste" to "नमस्ते", "namaskar" to "नमस्कार",
            // Common words
            "haan" to "हाँ", "nahi" to "नहीं", "hai" to "है",
            "hain" to "हैं", "ka" to "का", "ki" to "की", "ke" to "के",
            "ko" to "को", "se" to "से", "me" to "में", "par" to "पर",
            "aur" to "और", "ya" to "या", "kya" to "क्या",
            "kab" to "कब", "kahan" to "कहाँ", "kaise" to "कैसे",
            "kaun" to "कौन", "kyun" to "क्यों",
            // Pronouns
            "mujhe" to "मुझे", "mera" to "मेरा", "meri" to "मेरी",
            "mere" to "मेरे", "aap" to "आप", "tum" to "तुम",
            "aapka" to "आपका", "aapki" to "आपकी",
            // Time
            "aaj" to "आज", "kal" to "कल", "abhi" to "अभी",
            "subah" to "सुबह", "shaam" to "शाम", "raat" to "रात",
            "baje" to "बजे", "bajay" to "बजे", "din" to "दिन",
            // Verbs
            "karo" to "करो", "karna" to "करना", "batao" to "बताओ",
            "bolo" to "बोलो", "suno" to "सुनो", "dekho" to "देखो",
            "set" to "सेट", "alarm" to "अलार्म",
            // Helpful
            "bilkul" to "बिल्कुल", "theek" to "ठीक", "accha" to "अच्छा",
            "bahut" to "बहुत", "dhanyavaad" to "धन्यवाद",
            "shukriya" to "शुक्रिया",
            // Schedule related
            "schedule" to "शेड्यूल", "meeting" to "मीटिंग",
            "reminder" to "रिमाइंडर", "weather" to "वेदर",
            "forecast" to "फोरकास्ट", "next" to "नेक्स्ट",
            // Numbers
            "ek" to "एक", "do" to "दो", "teen" to "तीन",
            "char" to "चार", "paanch" to "पाँच",
            // Common phrases
            "gaya" to "गया", "gayi" to "गयी", "gaye" to "गए",
            "ho" to "हो", "hua" to "हुआ", "huyi" to "हुई",
            "kar" to "कर", "liye" to "लिए", "wala" to "वाला",
            "wali" to "वाली", "wale" to "वाले"
        )

        /**
         * Transliterates a single word from Roman/Latin to Devanagari.
         */
        fun transliterateWord(word: String): String {
            val lower = word.lowercase()
            // Check word map first for exact matches
            WORD_MAP[lower]?.let { return it }

            // Character-by-character transliteration
            var result = StringBuilder()
            var i = 0
            val text = lower
            while (i < text.length) {
                var matched = false
                // Try longest match first (up to 3 characters)
                for (len in minOf(3, text.length - i) downTo 1) {
                    val substr = text.substring(i, i + len)
                    TRANSLITERATION_MAP[substr]?.let {
                        result.append(it)
                        i += len
                        matched = true
                    }
                    if (matched) break
                }
                if (!matched) {
                    // Keep non-translatable characters as-is (digits, punctuation)
                    result.append(text[i])
                    i++
                }
            }
            return result.toString()
        }

        /**
         * Transliterates full Hinglish text to Devanagari, preserving punctuation and spacing.
         */
        fun transliterateToDevanagari(text: String): String {
            // Split into words keeping spaces as separators
            val words = text.split(" ")
            return words.joinToString(" ") { token ->
                if (token.isBlank()) {
                    token // preserve whitespace
                } else {
                    // Separate leading/trailing punctuation
                    val leadPunct = token.takeWhile { !it.isLetterOrDigit() }
                    val trailPunct = token.takeLastWhile { !it.isLetterOrDigit() }
                    val core = token.drop(leadPunct.length).dropLast(trailPunct.length)
                    if (core.isEmpty()) {
                        token
                    } else if (core.all { it.isDigit() }) {
                        token // keep numbers as-is
                    } else {
                        leadPunct + transliterateWord(core) + trailPunct
                    }
                }
            }
        }

        /**
         * Check if text contains any Devanagari characters.
         */
        fun hasDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

        /**
         * Check if text is primarily Latin script.
         */
        fun isPrimarilyLatin(text: String): Boolean {
            val letters = text.filter { it.isLetter() }
            if (letters.isEmpty()) return true
            val latinCount = letters.count { it.code in 0x0041..0x007A || it.code in 0x00C0..0x00FF }
            return latinCount.toFloat() / letters.length > 0.5f
        }
    }

    private var hindiTts: OfflineTts? = null
    private var englishTts: OfflineTts? = null

    init {
        initModels()
    }

    private fun initModels() {
        try {
            // espeak-ng-data must reside on the filesystem for C++ fopen access
            val espeakDir = prepareEspeakData(context)
            Log.d(TAG, "Prepared espeak-ng-data at: $espeakDir")

            // 1. Hindi Voice (hi_IN-rohan-medium) — optimized for clarity
            val hindiVits = OfflineTtsVitsModelConfig(
                model = "tts-hindi/hi_IN-rohan-medium.onnx",
                tokens = "tts-hindi/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.5f,    // Less noise for cleaner speech
                noiseScaleW = 0.7f,   // More natural prosody
                lengthScale = 1.0f    // Normal speed (was 1.05 = too slow)
            )
            val hindiModelConfig = OfflineTtsModelConfig(
                vits = hindiVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val hindiConfig = OfflineTtsConfig(model = hindiModelConfig)
            hindiTts = OfflineTts(context.assets, hindiConfig)
            Log.d(TAG, "Piper Hindi TTS initialized successfully")

            // 2. English Voice (en_US-amy-medium) — optimized for clarity
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
            Log.d(TAG, "Piper English TTS initialized successfully")
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
            // It's a file
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
            // It's a directory
            destFile.mkdirs()
            for (subName in assetList) {
                copyAssetFolder(context, "$srcName/$subName", dstDir)
            }
        }
    }

    /**
     * Synthesizes text on-device with intelligent script-based voice routing:
     * 
     * 1. Text already in Devanagari → Hindi TTS (native support)
     * 2. Latin-script text (even Hinglish) → English TTS (handles Latin script naturally)
     * 3. Mixed text → Split and synthesize each segment with appropriate voice, concatenate
     * 
     * For Hinglish text (Latin script detected as Hindi/Hinglish language), we route to
     * Hindi TTS with transliteration to Devanagari for more natural Hindi pronunciation.
     */
    fun synthesize(text: String, detectedLanguage: SpokenLanguage): GeneratedAudio? {
        if (text.isBlank()) return null

        return try {
            when {
                // Pure Devanagari text → Hindi TTS directly
                hasDevanagari(text) && !isPrimarilyLatin(text) -> {
                    Log.d(TAG, "Using Hindi TTS for Devanagari text")
                    (hindiTts ?: englishTts)?.generate(text, sid = 0, speed = 1.0f)
                }

                // Hinglish (Latin script but Hindi/Hinglish language) → Transliterate to Devanagari for Hindi TTS
                (detectedLanguage == SpokenLanguage.HINGLISH || detectedLanguage == SpokenLanguage.HINDI)
                    && isPrimarilyLatin(text) -> {
                    val devanagariText = transliterateToDevanagari(text)
                    Log.d(TAG, "Hinglish → Devanagari transliteration: '$text' → '$devanagariText'")
                    val result = (hindiTts ?: englishTts)?.generate(devanagariText, sid = 0, speed = 1.0f)
                    // Fallback to English TTS if Hindi TTS fails on transliterated text
                    result ?: run {
                        Log.w(TAG, "Hindi TTS failed on transliterated text, falling back to English TTS")
                        (englishTts ?: hindiTts)?.generate(text, sid = 0, speed = 1.0f)
                    }
                }

                // Pure English → English TTS
                else -> {
                    Log.d(TAG, "Using English TTS for Latin-script text")
                    (englishTts ?: hindiTts)?.generate(text, sid = 0, speed = 1.0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed for text: '$text', trying fallback", e)
            // Last resort fallback: try the other TTS engine
            try {
                (englishTts ?: hindiTts)?.generate(text, sid = 0, speed = 1.0f)
            } catch (e2: Exception) {
                Log.e(TAG, "All TTS synthesis attempts failed", e2)
                null
            }
        }
    }

    fun release() {
        hindiTts?.release()
        hindiTts = null
        englishTts?.release()
        englishTts = null
    }
}
