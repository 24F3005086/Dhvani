package com.sameer.dhvani.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    // Android Google Neural Male voice for authentic Hindi/Hinglish
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

            // 1. Piper Edge: English Voice (en_US-amy-medium) — Offline VITS
            val englishVits = OfflineTtsVitsModelConfig(
                model = "tts-english/en_US-amy-medium.onnx",
                tokens = "tts-english/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.33f,
                noiseScaleW = 0.5f,
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
            Log.d(TAG, "Piper Edge English TTS (Amy) initialized successfully")

            // 2. Piper Hindi Voice (hi_IN-rohan-medium) — Fallback offline VITS
            val hindiVits = OfflineTtsVitsModelConfig(
                model = "tts-hindi/hi_IN-rohan-medium.onnx",
                tokens = "tts-hindi/tokens.txt",
                dataDir = espeakDir,
                lexicon = "",
                dictDir = "",
                noiseScale = 0.33f,
                noiseScaleW = 0.5f,
                lengthScale = 1.1f
            )
            val hindiModelConfig = OfflineTtsModelConfig(
                vits = hindiVits,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val hindiConfig = OfflineTtsConfig(model = hindiModelConfig)
            hindiTts = OfflineTts(context.assets, hindiConfig)
            Log.d(TAG, "Piper Hindi TTS (Rohan) initialized as fallback")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Piper TTS models", e)
        }
    }

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTtsReady = true
                val hindiLocale = Locale.forLanguageTag("hi-IN")
                androidTts?.language = hindiLocale
                androidTts?.setSpeechRate(1.02f)
                androidTts?.setPitch(0.98f) // Natural deep male pitch

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
     * - ENGLISH: Synthesized with Piper Edge (Sherpa-ONNX en_US-amy-medium).
     * - HINGLISH / HINDI: Synthesized with On-Device Male Voice (Google Neural hi-in-x-hid-local).
     */
    fun synthesize(rawText: String, detectedLanguage: SpokenLanguage): GeneratedAudio? {
        val text = cleanTextForSpeech(rawText)
        if (text.isBlank()) return null

        return try {
            val isDevanagari = hasDevanagari(text)
            val isHinglishOrHindi = detectedLanguage == SpokenLanguage.HINGLISH || detectedLanguage == SpokenLanguage.HINDI

            if (isDevanagari || isHinglishOrHindi) {
                // HINDI / HINGLISH: Always synthesized with MALE VOICE
                val textToSpeak = if (isDevanagari) text else fallbackRomanToDevanagari(text)
                Log.d(TAG, "Speaking Hindi with Male Voice: '$textToSpeak'")

                val maleAudio = if (androidTtsReady && hasDevanagari(textToSpeak)) {
                    synthesizeWithMaleTts(textToSpeak)
                } else null

                // Fallback to Piper Rohan if Google TTS is unavailable
                maleAudio ?: run {
                    Log.w(TAG, "Male Google TTS unavailable, falling back to Piper Rohan")
                    hindiTts?.generate(textToSpeak, sid = 0, speed = 1.0f)
                }
            } else {
                // ENGLISH: Always synthesized with PIPER EDGE
                Log.d(TAG, "Speaking English with Piper Edge: '$text'")
                englishTts?.generate(text, sid = 0, speed = 1.0f)
                    ?: hindiTts?.generate(text, sid = 0, speed = 1.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed for text: '$text'", e)
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
            androidTts?.stop()
            androidTts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Android TTS", e)
        }
        androidTts = null
    }
}
