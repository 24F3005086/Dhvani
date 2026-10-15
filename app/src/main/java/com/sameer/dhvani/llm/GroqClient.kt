package com.sameer.dhvani.llm

import android.util.Log
import com.sameer.dhvani.BuildConfig
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
        private const val MODEL_STT = "whisper-large-v3"

        /**
         * Neutral bilingual prompt hint helps Whisper transcribe Indian accents
         * without biasing the language detection token distribution towards Hindi.
         */
        private const val STT_PROMPT_HINT =
            "Bilingual English and Hindi voice conversation with an AI assistant."

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
            SpokenLanguage.HINGLISH, SpokenLanguage.HINDI ->
                """You are Dhvani (ध्वनि), an AI voice assistant for IISc Bangalore. The user is speaking Hinglish/Hindi.
STRICT INSTRUCTIONS:
1. Respond ONLY in Hindi written in DEVANAGARI SCRIPT (हिंदी लिपि). Every single word must be Devanagari. NO Roman/Latin script at all.
2. Keep response to exactly 1 short spoken sentence (under 20 words).
3. Positively simulate and fulfill common requests without refusal:
   - For Bangalore weather: "कल बैंगलोर में मौसम सुहावना रहेगा, तापमान लगभग 28 डिग्री रहेगा।"
   - For alarm setting: "आपका अलार्म कल सुबह 7 बजे के लिए सेट कर दिया गया है।"
   - For schedule or next meeting: "आपकी अगली मीटिंग शाम 5 बजे है।"
   - For reminders: "आपका रिमाइंडर सेट कर दिया है।"
4. Do NOT use markdown, asterisks, bullet points, quotes, or English words.
5. Sound warm, natural, and conversational like a helpful Indian friend.
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
     *
     * CRITICAL: Returns TranscriptionResult with BOTH the transcript AND Whisper's
     * detected language code ("en", "hi", "ur", etc.). The pipeline uses whisperLanguage
     * as the PRIMARY language detection signal — it's far more accurate than text heuristics.
     *
     * Strategy:
     * 1. First attempt: Let Whisper auto-detect language (no forced language parameter)
     * 2. If result has Arabic/Urdu script → retry with language="en" forced
     * 3. Return both transcript and detected language for routing
     */
    fun transcribeAudio(samples: FloatArray): TranscriptionResult {
        // Attempt 1: Auto-detect language
        val autoResult = callWhisperAPI(samples, forcedLanguage = null)

        if (autoResult.transcript.isNotBlank()) {
            // Check for Arabic/Urdu hallucination
            if (hasArabicOrUrdu(autoResult.transcript)) {
                Log.w(TAG, "Auto-detect produced Arabic/Urdu: '${autoResult.transcript}', retrying with language=en")
                // Attempt 2: Force English transcription (romanizes Hindi words)
                val enResult = callWhisperAPI(samples, forcedLanguage = "en")
                if (enResult.transcript.isNotBlank()) {
                    // We know it was Hindi speech but forced English romanization
                    return TranscriptionResult(
                        transcript = enResult.transcript,
                        whisperLanguage = "hi" // We know it was Hindi that got Urdu hallucination
                    )
                }
            }
            return autoResult
        }

        return TranscriptionResult("", "en")
    }

    /**
     * Makes the actual Whisper API call. Separated to allow retry with different language.
     */
    private fun callWhisperAPI(samples: FloatArray, forcedLanguage: String?): TranscriptionResult {
        val wavBytes = pcmFloatToWav(samples, sampleRate = 16000)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", MODEL_STT)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("prompt", STT_PROMPT_HINT)

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
                val detectedLanguage = json.optString("language", "en")

                Log.d(TAG, "Groq STT: transcript='$rawTranscript' | whisperLang='$detectedLanguage' | forced='$forcedLanguage'")

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
