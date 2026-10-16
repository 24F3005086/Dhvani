package com.sameer.dhvani.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.sameer.dhvani.llm.GroqClient
import com.sameer.dhvani.utils.NumberNormalizer
import com.sameer.dhvani.utils.SpokenLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            "kar" to "कर", "karo" to "करो", "karna" to "करना", "karne" to "करने",
            "batao" to "बताओ", "bataiye" to "बताइए", "bolo" to "बोलो",
            "set" to "सेट", "alarm" to "अलार्म", "meeting" to "मीटिंग", "schedule" to "शेड्यूल",
            "scheduled" to "शेड्यूल", "weather" to "मौसम", "reminder" to "रिमाइंडर",
            "bilkul" to "बिल्कुल", "theek" to "ठीक", "thik" to "ठीक", "accha" to "अच्छा",
            "achha" to "अच्छा", "dhanyavaad" to "धन्यवाद", "shukriya" to "शुक्रिया",
            "gaya" to "गया", "gayi" to "गयी", "diya" to "दिया", "liye" to "लिए",
            "next" to "नेक्स्ट", "canteen" to "कैंटीन", "campus" to "कैंपस", "chai" to "चाय",
            "peeni" to "पीनी", "paas" to "पास", "traffic" to "ट्रैफिक", "degree" to "डिग्री",
            "temperature" to "तापमान", "celsius" to "सेल्सियस", "iisc" to "आईआईएससी",
            "bangalore" to "बैंगलोर", "time" to "टाइम", "office" to "ऑफिस", "help" to "मदद",
            "madad" to "मदद", "doctor" to "डॉक्टर", "lab" to "लैब", "forecast" to "पूर्वानुमान",
            "wahi" to "वहीं", "jaaiye" to "जाइए", "main" to "मेन", "bache" to "बचे",
            "bachte" to "बचते", "rupaye" to "रुपये", "rupees" to "रुपये", "ghatane" to "घटाने",
            "kharch" to "खर्च", "suhana" to "सुहावना", "suhavana" to "सुहावना",
            "hi" to "ही", "bhi" to "भी", "toh" to "तो", "to" to "तो", "agar" to "अगर",
            "kitne" to "कितने", "kitna" to "कितना", "kitni" to "कितनी", "chahiye" to "चाहिए",
            "distance" to "डिस्टेंस", "distens" to "डिस्टेंस",
            "kilometer" to "किलोमीटर", "kilometre" to "किलोमीटर", "kilometers" to "किलोमीटर", "km" to "किलोमीटर",
            "chennai" to "चेन्नई", "cheneee" to "चेन्नई", "chenai" to "चेन्नई",
            "delhi" to "दिल्ली", "mumbai" to "मुंबई", "hyderabad" to "हैदराबाद",
            "lagbhag" to "लगभग", "agli" to "अगली", "pehli" to "पहली",
            "city" to "सिटी", "center" to "सेंटर", "centre" to "सेंटर", "airport" to "एयरपोर्ट",
            "route" to "रूट", "map" to "मैप", "speed" to "स्पीड", "train" to "ट्रेन", "flight" to "फ्लाइट",
            "bengaluru" to "बैंगलोर", "benglorb" to "बैंगलोर", "nekst" to "नेक्स्ट"
        )

        private val CONSONANT_MAP = mapOf(
            "bh" to "भ", "ch" to "च", "chh" to "छ", "dh" to "ध", "gh" to "घ",
            "jh" to "झ", "kh" to "ख", "ph" to "फ", "sh" to "श", "th" to "थ",
            "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द",
            "n" to "न", "p" to "प", "b" to "ब", "m" to "म", "y" to "य",
            "r" to "र", "l" to "ल", "v" to "व", "w" to "व", "s" to "स",
            "h" to "ह", "f" to "फ", "z" to "ज़", "c" to "क", "ce" to "स", "ci" to "सि", "cy" to "सि"
        )

        private val VOWEL_MATRA_MAP = mapOf(
            "aa" to "ा", "ee" to "ी", "oo" to "ू", "ai" to "ै", "au" to "ौ",
            "a" to "", "i" to "ि", "u" to "ु", "e" to "े", "o" to "ो"
        )

        private val INITIAL_VOWEL_MAP = mapOf(
            "aa" to "आ", "ee" to "ई", "oo" to "ऊ", "ai" to "ऐ", "au" to "औ",
            "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए", "o" to "ओ"
        )

        fun hasDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

        fun cleanTextForSpeech(text: String): String {
            return GroqClient.stripThinkingTags(text)
                .replace(Regex("[*#_`~><\\[\\]{}|\\\\]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun phoneticTransliterateWord(word: String): String {
            val w = word.lowercase()
            val sb = java.lang.StringBuilder()
            var i = 0
            var isStart = true

            while (i < w.length) {
                // Match initial independent vowels
                if (isStart && i + 2 <= w.length && INITIAL_VOWEL_MAP.containsKey(w.substring(i, i + 2))) {
                    sb.append(INITIAL_VOWEL_MAP[w.substring(i, i + 2)])
                    i += 2
                    isStart = false
                    continue
                }
                if (isStart && INITIAL_VOWEL_MAP.containsKey(w.substring(i, i + 1))) {
                    sb.append(INITIAL_VOWEL_MAP[w.substring(i, i + 1)])
                    i += 1
                    isStart = false
                    continue
                }

                // Match consonants
                var cMatched: String? = null
                if (i + 2 <= w.length && CONSONANT_MAP.containsKey(w.substring(i, i + 2))) {
                    cMatched = CONSONANT_MAP[w.substring(i, i + 2)]
                    i += 2
                } else if (CONSONANT_MAP.containsKey(w.substring(i, i + 1))) {
                    cMatched = CONSONANT_MAP[w.substring(i, i + 1)]
                    i += 1
                }

                if (cMatched != null) {
                    sb.append(cMatched)
                    isStart = false
                    // Lookahead for following vowel matra
                    if (i + 2 <= w.length && VOWEL_MATRA_MAP.containsKey(w.substring(i, i + 2))) {
                        sb.append(VOWEL_MATRA_MAP[w.substring(i, i + 2)])
                        i += 2
                    } else if (i < w.length && VOWEL_MATRA_MAP.containsKey(w.substring(i, i + 1))) {
                        sb.append(VOWEL_MATRA_MAP[w.substring(i, i + 1)])
                        i += 1
                    }
                } else {
                    sb.append(w[i])
                    i += 1
                    isStart = false
                }
            }
            return sb.toString()
        }

        fun fallbackRomanToDevanagari(text: String): String {
            val words = text.split(" ")
            return words.joinToString(" ") { word ->
                val cleanWord = word.lowercase().trim { !it.isLetterOrDigit() }
                val dev = COMMON_HINGLISH_WORDS[cleanWord]
                val lead = word.takeWhile { !it.isLetterOrDigit() }
                val trail = word.takeLastWhile { !it.isLetterOrDigit() }

                if (dev != null) {
                    "$lead$dev$trail"
                } else if (cleanWord.all { it in 'a'..'z' } && cleanWord.length >= 2) {
                    val transliterated = phoneticTransliterateWord(cleanWord)
                    "$lead$transliterated$trail"
                } else {
                    word
                }
            }
        }
    }

    private var hindiTts: OfflineTts? = null
    private var englishTts: OfflineTts? = null
    private var supertonicTts: OfflineTts? = null

    private val supertonicLock = Any()
    private val englishLock = Any()
    private val hindiLock = Any()

    // Android Google Neural Male voice for fallback Hindi/Hinglish
    private var androidTts: TextToSpeech? = null
    @Volatile private var androidTtsReady = false

    init {
        initModels()
        initAndroidTts()
    }

    private fun initModels() {
        try {
            val espeakDir = prepareEspeakData(context)
            Log.d(TAG, "Prepared espeak-ng-data at: $espeakDir")

            // 1. English Voice (Amy medium) — Offline VITS
            // lengthScale = 1.15f provides a clear, natural, deliberate conversational pace (no rushing)
            val englishVits = OfflineTtsVitsModelConfig(
                model = "tts-english/en_US-amy-medium.onnx",
                tokens = "tts-english/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.33f,
                noiseScaleW = 0.5f,
                lengthScale = 1.15f
            )
            val englishModelConfig = OfflineTtsModelConfig(
                vits = englishVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val englishConfig = OfflineTtsConfig(model = englishModelConfig)
            englishTts = OfflineTts(context.assets, englishConfig)
            Log.d(TAG, "English TTS initialized successfully (sampleRate=${englishTts?.sampleRate()})")

            // 2. Supertonic 3 Hindi (sherpa-onnx Flow-Matching Diffusion) — Primary Hindi Voice
            try {
                val supertonicModel = OfflineTtsSupertonicModelConfig(
                    durationPredictor = "supertonic-3-hi/duration_predictor.int8.onnx",
                    textEncoder = "supertonic-3-hi/text_encoder.int8.onnx",
                    vectorEstimator = "supertonic-3-hi/vector_estimator.int8.onnx",
                    vocoder = "supertonic-3-hi/vocoder.int8.onnx",
                    ttsJson = "supertonic-3-hi/tts.json",
                    unicodeIndexer = "supertonic-3-hi/unicode_indexer.bin",
                    voiceStyle = "supertonic-3-hi/voice.bin"
                )
                val supertonicOfflineConfig = OfflineTtsModelConfig(
                    supertonic = supertonicModel,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                )
                val supertonicConfig = OfflineTtsConfig(model = supertonicOfflineConfig)
                supertonicTts = OfflineTts(context.assets, supertonicConfig)
                Log.d(TAG, "Supertonic 3 Hindi TTS initialized successfully (sampleRate=${supertonicTts?.sampleRate()})")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Supertonic 3 Hindi TTS", e)
            }

            // 3. Piper Hindi Voice (Rohan medium) — Fallback offline VITS
            val hindiVits = OfflineTtsVitsModelConfig(
                model = "tts-hindi/hi_IN-rohan-medium.onnx",
                tokens = "tts-hindi/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.33f,
                noiseScaleW = 0.5f,
                lengthScale = 1.15f
            )
            val hindiModelConfig = OfflineTtsModelConfig(
                vits = hindiVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val hindiConfig = OfflineTtsConfig(model = hindiModelConfig)
            hindiTts = OfflineTts(context.assets, hindiConfig)
            Log.d(TAG, "Hindi fallback TTS initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS models", e)
        }
    }

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTtsReady = true
                val hindiLocale = Locale.forLanguageTag("hi-IN")
                androidTts?.language = hindiLocale
                androidTts?.setSpeechRate(1.0f)
                androidTts?.setPitch(0.98f) // Natural deep pitch

                // Select Google Neural Male Voice: hi-in-x-hid-local
                val voices = androidTts?.voices ?: emptySet()
                val maleVoice = voices.firstOrNull { it.name == "hi-in-x-hid-local" }
                    ?: voices.firstOrNull { it.locale.language == "hi" && it.name.contains("hid") }
                    ?: voices.firstOrNull { it.locale.language == "hi" }

                if (maleVoice != null) {
                    androidTts?.voice = maleVoice
                    Log.d(TAG, "Google Neural Male Hindi voice selected: ${maleVoice.name}")
                }
            } else {
                Log.w(TAG, "Android TTS initialization failed with status=$status")
                androidTtsReady = false
            }
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
     * Dedicated Dual-Voice Routing:
     * - ENGLISH: Synthesized with On-Device English Voice (Amy).
     * - HINGLISH / HINDI: Primary voice is Supertonic 3 Hindi (flow-matching diffusion with 4 threads and 5 steps for pristine clarity).
     *   Fallbacks: Piper Rohan VITS, Android Google Neural Male voice.
     *   Numbers like 5 and 7 are fully normalized to Devanagari words (पाँच, सात) so pronunciation is flawless.
     */
    fun synthesize(rawText: String, detectedLanguage: SpokenLanguage, isFirstChunk: Boolean = false): GeneratedAudio? {
        val text = cleanTextForSpeech(rawText)
        if (text.isBlank()) return null

        return try {
            val isDevanagari = hasDevanagari(text)
            val isHinglishOrHindi = detectedLanguage == SpokenLanguage.HINGLISH || detectedLanguage == SpokenLanguage.HINDI

            if (isDevanagari || isHinglishOrHindi) {
                // 1. Text normalization: Convert Romanized Hinglish and expand all digits (5 -> पाँच, 7 -> सात, 5 PM -> शाम पाँच बजे)
                val baseDevanagari = if (isDevanagari) text else fallbackRomanToDevanagari(text)
                val normalizedHindi = NumberNormalizer.normalizeForHindi(baseDevanagari)

                // If text has NO Devanagari characters at all, it's an English sentence — speak with English voice!
                if (!hasDevanagari(normalizedHindi)) {
                    val normalizedEnglish = NumberNormalizer.normalizeForEnglish(text)
                    Log.d(TAG, "No Devanagari characters found, routing to English TTS: '$normalizedEnglish'")
                    return synchronized(englishLock) {
                        englishTts?.generate(normalizedEnglish, sid = 0, speed = 1.0f)
                    }
                }

                // Ensure Hindi text has terminal punctuation for clean prosodic cadence closure
                val textWithPunctuation = if (!normalizedHindi.endsWith("।") && !normalizedHindi.endsWith(".") && !normalizedHindi.endsWith("?") && !normalizedHindi.endsWith("!")) {
                    "$normalizedHindi।"
                } else {
                    normalizedHindi
                }

                val steps = 5
                Log.d(TAG, "Speaking Hindi (isFirstChunk=$isFirstChunk, steps=$steps): original='$text' -> normalized='$textWithPunctuation'")

                // 1. Primary: Supertonic 3 Hindi (sherpa-onnx Flow-Matching Diffusion, numSteps=5, speed=0.95f for crystal-clear articulation)
                val supertonicAudio = try {
                    if (supertonicTts != null) {
                        Log.d(TAG, "Synthesizing with Supertonic 3 Hindi (numSteps=5, speed=0.95f, 4 threads)...")
                        val genConfig = GenerationConfig(
                            sid = 0,
                            numSteps = steps,
                            speed = 0.95f,
                            extra = mapOf("lang" to "hi")
                        )
                        val audio = synchronized(supertonicLock) {
                            supertonicTts?.generateWithConfig(textWithPunctuation, genConfig)
                        }
                        if (audio != null && audio.samples.isNotEmpty()) {
                            Log.d(TAG, "Supertonic 3 generated ${audio.samples.size} samples @ ${audio.sampleRate}Hz (steps=$steps)")
                            audio
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Supertonic 3 synthesis failed, attempting fallback", e)
                    null
                }

                if (supertonicAudio != null) {
                    return supertonicAudio
                }

                // 2. Fallback: Piper Rohan VITS (Single-pass on-device, ~180ms)
                val rohanAudio = synchronized(hindiLock) {
                    hindiTts?.generate(textWithPunctuation, sid = 0, speed = 1.0f)
                }
                if (rohanAudio != null && rohanAudio.samples.isNotEmpty()) {
                    Log.d(TAG, "Piper Rohan fallback generated ${rohanAudio.samples.size} samples @ ${rohanAudio.sampleRate}Hz")
                    return rohanAudio
                }

                // 3. Fallback: Android Google Neural Voice (hi-in-x-hid-local)
                val maleAudio = if (androidTtsReady && hasDevanagari(textWithPunctuation)) {
                    synthesizeWithMaleTts(textWithPunctuation)
                } else null
                return maleAudio
            } else {
                // ENGLISH: Always synthesized with English Voice at natural conversational pace (1.0x)
                val normalizedEnglish = NumberNormalizer.normalizeForEnglish(text)
                Log.d(TAG, "Speaking English: '$normalizedEnglish'")
                synchronized(englishLock) {
                    englishTts?.generate(normalizedEnglish, sid = 0, speed = 1.0f)
                } ?: synchronized(hindiLock) {
                    hindiTts?.generate(normalizedEnglish, sid = 0, speed = 1.0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed for text: '$rawText'", e)
            null
        }
    }

    /**
     * Synthesizes Hindi text with the on-device Male neural voice (hi-in-x-hid-local).
     */
    private fun synthesizeWithMaleTts(text: String): GeneratedAudio? {
        val tts = androidTts ?: return null
        if (!androidTtsReady) return null

        try {
            val locale = Locale.forLanguageTag("hi-IN")
            tts.language = locale

            val voices = tts.voices ?: emptySet()
            val maleVoice = voices.firstOrNull { it.name == "hi-in-x-hid-local" }
                ?: voices.firstOrNull { it.locale.language == "hi" && it.name.contains("hid") }
                ?: voices.firstOrNull { it.locale.language == "hi" }

            if (maleVoice != null) {
                tts.voice = maleVoice
            }

            val tempFile = File(context.cacheDir, "male_hindi_${System.currentTimeMillis()}.wav")
            val latch = CountDownLatch(1)
            val success = java.util.concurrent.atomic.AtomicBoolean(false)

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    success.set(true)
                    latch.countDown()
                }
                @Deprecated("Deprecated in API")
                override fun onError(utteranceId: String?) {
                    success.set(false)
                    latch.countDown()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "Male TTS error: code=$errorCode")
                    success.set(false)
                    latch.countDown()
                }
            })

            val result = tts.synthesizeToFile(text, null, tempFile, "male_dhvani_${System.currentTimeMillis()}")
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Male TTS synthesizeToFile error: $result")
                return null
            }

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Log.e(TAG, "Male TTS synthesis timed out")
                tempFile.delete()
                return null
            }

            if (!success.get() || !tempFile.exists() || tempFile.length() < 100) {
                Log.e(TAG, "Male TTS synthesis failed or file too small")
                tempFile.delete()
                return null
            }

            val audio = readWavAsGeneratedAudio(tempFile)
            tempFile.delete()
            return audio

        } catch (e: Exception) {
            Log.e(TAG, "Male TTS synthesis exception", e)
            return null
        }
    }

    private fun readWavAsGeneratedAudio(wavFile: File): GeneratedAudio? {
        try {
            RandomAccessFile(wavFile, "r").use { raf ->
                val riff = ByteArray(4)
                raf.readFully(riff)
                if (String(riff) != "RIFF") {
                    Log.e(TAG, "Not a valid WAV file")
                    return null
                }

                raf.skipBytes(4)
                val wave = ByteArray(4)
                raf.readFully(wave)
                if (String(wave) != "WAVE") {
                    Log.e(TAG, "Not a valid WAVE file")
                    return null
                }

                var sampleRate = 22050
                var numChannels = 1
                var bitsPerSample = 16
                var dataSize = 0
                var dataOffset = 0L

                while (raf.filePointer < raf.length()) {
                    val chunkId = ByteArray(4)
                    if (raf.read(chunkId) < 4) break
                    val chunkIdStr = String(chunkId)

                    val sizeBytes = ByteArray(4)
                    if (raf.read(sizeBytes) < 4) break
                    val chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int

                    when (chunkIdStr) {
                        "fmt " -> {
                            val fmtData = ByteArray(chunkSize)
                            raf.readFully(fmtData)
                            val fmt = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                            fmt.short
                            numChannels = fmt.short.toInt()
                            sampleRate = fmt.int
                            fmt.int
                            fmt.short
                            bitsPerSample = fmt.short.toInt()
                        }
                        "data" -> {
                            dataSize = chunkSize
                            dataOffset = raf.filePointer
                            break
                        }
                        else -> {
                            raf.skipBytes(chunkSize)
                        }
                    }
                }

                if (dataSize == 0 || dataOffset == 0L) {
                    Log.e(TAG, "No data chunk found in WAV")
                    return null
                }

                raf.seek(dataOffset)
                val rawData = ByteArray(dataSize)
                raf.readFully(rawData)

                val bytesPerSample = bitsPerSample / 8
                val totalSamples = dataSize / (bytesPerSample * numChannels)
                val samples = FloatArray(totalSamples)
                val buf = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN)

                for (i in 0 until totalSamples) {
                    if (bitsPerSample == 16) {
                        val sample = buf.short
                        for (ch in 1 until numChannels) {
                            if (buf.hasRemaining()) buf.short
                        }
                        samples[i] = sample / 32768.0f
                    } else if (bitsPerSample == 8) {
                        val sample = buf.get()
                        for (ch in 1 until numChannels) {
                            if (buf.hasRemaining()) buf.get()
                        }
                        samples[i] = (sample - 128) / 128.0f
                    }
                }

                Log.d(TAG, "Male WAV generated: ${samples.size} samples @ ${sampleRate}Hz, ${bitsPerSample}bit")
                return GeneratedAudio(samples, sampleRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file", e)
            return null
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

        try {
            supertonicTts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Supertonic TTS", e)
        }
        supertonicTts = null

        try {
            androidTts?.stop()
            androidTts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Android TTS", e)
        }
        androidTts = null
    }
}
