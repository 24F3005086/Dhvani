package com.sameer.dhvani.llm

import android.util.Log
import com.sameer.dhvani.BuildConfig
import com.sameer.dhvani.pipeline.AssistantMode
import com.sameer.dhvani.utils.SpokenLanguage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Transcription result from Groq Cloud Whisper.
 * Carries the raw transcript AND Whisper's own language detection.
 */
data class TranscriptionResult(
    val transcript: String,
    val whisperLanguage: String, // "en", "hi", "ur", etc.
    val isOnDevice: Boolean = false
)

class GroqClient(
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_TRANSCRIPTIONS_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL_PRIMARY = "qwen/qwen3.8-27b"
        private const val MODEL_STT = "whisper-large-v3" // Full 32-layer decoder for zero phonetic hallucinations

        private const val PROMPT_HINT_ENGLISH =
            "Dhvani voice assistant for IISc Bangalore. Bangalore, Chennai, Delhi, Mumbai. Next meeting, schedule, calendar. Distance, kilometer, route, traffic. Bangalore weather, temperature, alarms at 7 AM, campus canteen, calculations, science, technology."

        private const val PROMPT_HINT_HINGLISH =
            "Dhvani voice assistant for IISc Bangalore. Chennai se Bangalore ka distance kitna hai, meri next meeting kab hai, Bangalore ka weather kaisa hai, meeting schedule, kal subah 7 baje ka alarm laga do, campus mein nearest canteen kahan hai, chai peeni hai, 500 rupaye mein se 150 kharch ho gaye toh kitne bache, namaste, schedule kya hai, distance, kilometer."

        private const val PROMPT_HINT_HINDI =
            "ध्वनि, आईआईएससी बैंगलोर। चेन्नई से बैंगलोर की दूरी या डिस्टेंस कितना है, अगली मीटिंग, कल बैंगलोर का मौसम कैसा रहेगा, कल सुबह सात बजे का अलार्म लगा दो, कैंटीन कहाँ है, पाँच सौ रुपये में से एक सौ पचास रुपये खर्च हो गए तो कितने बचे, भारत के प्रधानमंत्री कौन हैं, किलोमीटर।"

        private const val PROMPT_HINT_AUTO =
            "Dhvani voice assistant for IISc Bangalore. Chennai, Bangalore, Delhi, Mumbai. Next meeting, schedule, calendar. Distance, kilometer, route. Weather, temperature, alarm, campus canteen, chai, 500 rupaye, 150 kharch ho gaye, kitne bache, namaste, मौसम, प्रधानमंत्री, रुपये, खर्च, चेन्नई, दूरी, किलोमीटर।"

        fun stripThinkingTags(text: String): String {
            return text
                .replace(Regex("(?s)<think>.*?</think>"), "")
                .replace(Regex("(?s)<think>.*"), "")
                .replace("<think>", "")
                .replace("</think>", "")
                .trim()
        }

        fun hasArabicOrUrdu(text: String): Boolean {
            return text.any { it.code in 0x0600..0x06FF }
        }

        fun pcmFloatToWav(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
            val numChannels: Short = 1
            val bitsPerSample: Short = 16
            val bytesPerSample = bitsPerSample / 8
            val byteRate = sampleRate * numChannels * bytesPerSample
            val blockAlign = (numChannels * bytesPerSample).toShort()
            val dataSize = samples.size * bytesPerSample
            val chunkSize = 36 + dataSize

            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put('R'.code.toByte()); buffer.put('I'.code.toByte())
            buffer.put('F'.code.toByte()); buffer.put('F'.code.toByte())
            buffer.putInt(chunkSize)
            buffer.put('W'.code.toByte()); buffer.put('A'.code.toByte())
            buffer.put('V'.code.toByte()); buffer.put('E'.code.toByte())

            // fmt sub-chunk
            buffer.put('f'.code.toByte()); buffer.put('m'.code.toByte())
            buffer.put('t'.code.toByte()); buffer.put(' '.code.toByte())
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(numChannels)
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign)
            buffer.putShort(bitsPerSample)

            // data sub-chunk
            buffer.put('d'.code.toByte()); buffer.put('a'.code.toByte())
            buffer.put('t'.code.toByte()); buffer.put('a'.code.toByte())
            buffer.putInt(dataSize)

            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val pcm = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(pcm)
            }

            return buffer.array()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sttClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentEventSource: EventSource? = null

    /**
     * Streams LLM response for the designated assistant (English vs Hinglish).
     * System prompt strictly controls response language to match what the user spoke.
     */
    fun streamResponse(
        userMessage: String,
        detectedLanguage: SpokenLanguage,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onToken: (String) -> Unit,
        onComplete: (fullResponse: String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        cancel()

        val systemPrompt = when (detectedLanguage) {
            SpokenLanguage.HINGLISH ->
                """You are Dhvani (ध्वनि), an AI voice assistant for IISc Bangalore. The user is speaking in Hinglish (Hindi written in Roman/Latin script).
STRICT INSTRUCTIONS:
1. Respond in natural, conversational Hinglish written in ROMAN/LATIN SCRIPT (how people text in India).
2. Do NOT use Devanagari script for Hinglish. Use natural Roman spelling (e.g., "Aapka alarm kal subah 7 baje ke liye set kar diya gaya hai.").
3. Keep response to exactly 1 natural, flowing spoken sentence (under 15 words).
4. Positively simulate and fulfill common requests without refusal:
   - For Bangalore weather: "Kal Bangalore mein mausam suhana rahega aur temperature 28 degree hoga."
   - For alarm setting: "Aapka alarm kal subah 7 baje ke liye set kar diya gaya hai."
   - For schedule or next meeting: "Aapki agli meeting aaj shaam 5 baje scheduled hai."
   - For reminders: "Aapka reminder set kar diya gaya hai."
   - For canteen/chai: "IISc campus ki main canteen aapke bilkul paas hi hai."
   - For math/calculations: "500 rupaye mein se 150 kharch karne par 350 rupaye bachte hain."
5. Do NOT use markdown, asterisks, bullet points, or quotes.
6. Sound warm, helpful, and natural.
/no_think"""

            SpokenLanguage.HINDI ->
                """You are Dhvani (ध्वनि), an AI voice assistant for IISc Bangalore. The user is speaking in pure Hindi.
STRICT INSTRUCTIONS:
1. Respond ONLY in Hindi written in DEVANAGARI SCRIPT (हिंदी लिपि). Every single word must be Devanagari.
2. Keep response to exactly 1 natural, flowing spoken sentence (under 15 words).
3. Positively simulate and fulfill common requests without refusal:
   - For Bangalore weather: "कल बैंगलोर में मौसम सुहावना रहेगा और तापमान लगभग अट्ठाईस डिग्री रहेगा।"
   - For alarm setting: "आपका अलार्म कल सुबह सात बजे के लिए सेट कर दिया गया है।"
   - For schedule or next meeting: "आपकी अगली मीटिंग आज शाम पाँच बजे है।"
   - For reminders: "आपका रिमाइंडर सेट कर दिया गया है।"
   - For canteen/chai: "आईआईएससी कैंपस की मुख्य कैंटीन आपके पास ही में है।"
   - For math/calculations: "पाँच सौ में से एक सौ पचास घटाने पर तीन सौ पचास रुपये बचे हैं।"
4. Do NOT use markdown, asterisks, bullet points, quotes, or English words.
5. Sound warm, natural, and conversational.
/no_think"""

            SpokenLanguage.ENGLISH ->
                """You are Dhvani, an ultra-fast voice assistant for IISc Bangalore. The user is speaking English.
STRICT INSTRUCTIONS:
1. Respond ONLY in clear, natural English. Do NOT mix any Hindi words.
2. Keep response to exactly 1 short spoken sentence (under 20 words).
3. Positively simulate and fulfill common requests:
   - For weather: "Tomorrow's weather in Bangalore is expected to be pleasant, around 28 degrees Celsius."
   - For alarm setting: "Sure, your alarm for 7 AM tomorrow has been set."
   - For reminders: "Got it, your reminder for the 5 PM meeting is set."
   - For schedule: "Your next meeting is scheduled for 5 PM today."
   - For math/calculations: "24 multiplied by 4 is 96."
4. Do NOT use markdown, asterisks, bullet points, or quotes.
/no_think"""
        }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            for ((role, content) in conversationHistory) {
                put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val requestPayload = JSONObject().apply {
            put("model", MODEL_PRIMARY)
            put("stream", true)
            put("temperature", 0.5)
            put("max_tokens", 100) // Shorter = faster response
            put("messages", messagesArray)
        }

        val request = Request.Builder()
            .url(GROQ_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val fullResponseBuilder = StringBuilder()

        val listener = object : EventSourceListener() {
            @Volatile
            private var completed = false
            private var inThinkBlock = false

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Groq SSE connection established")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    if (!completed) {
                        completed = true
                        val responseText = stripThinkingTags(fullResponseBuilder.toString())
                        onComplete(responseText)
                    }
                    return
                }

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val rawToken = delta?.optString("content", "") ?: ""
                        if (rawToken.isNotEmpty()) {
                            val cleanToken = processStreamToken(rawToken)
                            if (cleanToken.isNotEmpty()) {
                                fullResponseBuilder.append(cleanToken)
                                onToken(cleanToken)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE chunk: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!completed) {
                    completed = true
                    val responseText = stripThinkingTags(fullResponseBuilder.toString())
                    if (responseText.isNotEmpty()) {
                        onComplete(responseText)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Groq stream failure. Response code: ${response?.code}", t)
                if (!completed) {
                    completed = true
                    onError(t ?: Exception("Groq HTTP error: ${response?.code}"))
                }
            }

            private fun processStreamToken(token: String): String {
                var currentText = token
                val output = StringBuilder()

                while (currentText.isNotEmpty()) {
                    if (inThinkBlock) {
                        val thinkEndIdx = currentText.indexOf("</think>")
                        if (thinkEndIdx != -1) {
                            inThinkBlock = false
                            currentText = currentText.substring(thinkEndIdx + "</think>".length)
                        } else {
                            currentText = ""
                        }
                    } else {
                        val thinkStartIdx = currentText.indexOf("<think>")
                        if (thinkStartIdx != -1) {
                            output.append(currentText.substring(0, thinkStartIdx))
                            inThinkBlock = true
                            currentText = currentText.substring(thinkStartIdx + "<think>".length)
                        } else {
                            output.append(currentText)
                            currentText = ""
                        }
                    }
                }

                return output.toString()
                    .replace("<think>", "")
                    .replace("</think>", "")
            }
        }

        currentEventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    /**
     * Transcribes raw audio samples at 16kHz mono using Groq Cloud Whisper Large V3.
     * Takes the selected AssistantMode to provide tailored language constraints and vocabulary prompts.
     */
    fun transcribeAudio(
        samples: FloatArray,
        mode: AssistantMode = AssistantMode.AUTO,
        sampleRate: Int = 16000
    ): TranscriptionResult {
        return when (mode) {
            AssistantMode.ENGLISH -> {
                callWhisperAPI(samples, forcedLanguage = "en", promptHint = PROMPT_HINT_ENGLISH, sampleRate = sampleRate)
            }
            AssistantMode.HINGLISH -> {
                // In Hinglish mode, priming with Roman Hinglish prompts Whisper to transcribe in clean Latin script
                callWhisperAPI(samples, forcedLanguage = "en", promptHint = PROMPT_HINT_HINGLISH, sampleRate = sampleRate)
            }
            AssistantMode.HINDI -> {
                // In pure Hindi mode, force language=hi for flawless Devanagari script
                callWhisperAPI(samples, forcedLanguage = "hi", promptHint = PROMPT_HINT_HINDI, sampleRate = sampleRate)
            }
            AssistantMode.AUTO -> {
                val autoResult = callWhisperAPI(samples, forcedLanguage = null, promptHint = PROMPT_HINT_AUTO, sampleRate = sampleRate)
                if (autoResult.transcript.isNotBlank()) {
                    // Check for Arabic/Urdu script: If Whisper produced Arabic/Urdu script characters,
                    // force Hindi (hi) so it outputs pure Devanagari Hindi!
                    if (hasArabicOrUrdu(autoResult.transcript)) {
                        Log.w(TAG, "Whisper produced Arabic/Urdu script: '${autoResult.transcript}', re-transcribing with language=hi")
                        val hiResult = callWhisperAPI(samples, forcedLanguage = "hi", promptHint = PROMPT_HINT_HINDI, sampleRate = sampleRate)
                        if (hiResult.transcript.isNotBlank()) {
                            return TranscriptionResult(
                                transcript = hiResult.transcript,
                                whisperLanguage = "Hindi"
                            )
                        }
                    }
                    autoResult
                } else {
                    TranscriptionResult("", "English")
                }
            }
        }
    }

    /**
     * Makes the actual Whisper API call with whisper-large-v3.
     */
    private fun callWhisperAPI(
        samples: FloatArray,
        forcedLanguage: String?,
        promptHint: String,
        sampleRate: Int = 16000
    ): TranscriptionResult {
        val wavBytes = pcmFloatToWav(samples, sampleRate = sampleRate)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", MODEL_STT)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("prompt", promptHint)

        // Only force language if specified
        if (forcedLanguage != null) {
            multipartBuilder.addFormDataPart("language", forcedLanguage)
        }

        val request = Request.Builder()
            .url(GROQ_TRANSCRIPTIONS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        return try {
            sttClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Groq STT error: HTTP ${response.code} $err")
                    return TranscriptionResult("", "")
                }

                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val rawTranscript = json.optString("text", "").trim()
                val detectedLanguage = json.optString("language", "")

                Log.d(TAG, "Groq STT ($MODEL_STT): transcript='$rawTranscript' | whisperLang='$detectedLanguage' | forced='$forcedLanguage'")

                TranscriptionResult(
                    transcript = rawTranscript,
                    whisperLanguage = detectedLanguage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq STT transcription failed", e)
            TranscriptionResult("", "")
        }
    }

    fun cancel() {
        currentEventSource?.cancel()
        currentEventSource = null
    }
}
