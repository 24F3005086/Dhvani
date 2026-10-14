package com.sameer.dhvani.llm

import android.util.Log
import com.sameer.dhvani.BuildConfig
import com.sameer.dhvani.utils.LanguageDetector
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

class GroqClient(
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_TRANSCRIPTIONS_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL_PRIMARY = "qwen/qwen3.8-27b"
        private const val MODEL_FALLBACK = "openai/gpt-oss-20b"
        private const val MODEL_STT = "whisper-large-v3"

        fun stripThinkingTags(text: String): String {
            return text
                .replace(Regex("(?s)<think>.*?</think>"), "")
                .replace(Regex("(?s)<think>.*"), "")
                .replace("<think>", "")
                .replace("</think>", "")
                .trim()
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
            buffer.put('R'.code.toByte())
            buffer.put('I'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.putInt(chunkSize)
            buffer.put('W'.code.toByte())
            buffer.put('A'.code.toByte())
            buffer.put('V'.code.toByte())
            buffer.put('E'.code.toByte())

            // "fmt " sub-chunk
            buffer.put('f'.code.toByte())
            buffer.put('m'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put(' '.code.toByte())
            buffer.putInt(16) // Subchunk1Size (16 for PCM)
            buffer.putShort(1) // AudioFormat (1 for PCM)
            buffer.putShort(numChannels) // NumChannels (1 for mono)
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign)
            buffer.putShort(bitsPerSample)

            // "data" sub-chunk
            buffer.put('d'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.putInt(dataSize)

            // PCM 16-bit signed samples
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
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for SSE stream
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var currentEventSource: EventSource? = null

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
                "You are Dhvani (ध्वनि), an ultra-fast on-device voice assistant for IISc Bangalore. " +
                "The user spoke in Hindi or Hinglish. Respond in natural, conversational Hinglish (Hindi written in Roman/Latin script, or simple Devanagari). " +
                "Keep responses concise (1 to 2 spoken sentences maximum). Do not use bullet points, bold text, or markdown. /no_think"
            SpokenLanguage.ENGLISH ->
                "You are Dhvani, an ultra-fast on-device voice assistant for IISc Bangalore. " +
                "Respond in clear, natural, conversational English. " +
                "Keep responses concise (1 to 2 spoken sentences maximum). Do not use bullet points, bold text, or markdown. /no_think"
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
            put("temperature", 0.6)
            put("max_tokens", 200)
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
     * Transcribes raw audio samples at 16kHz mono using Groq Cloud Whisper.
     * Converts FloatArray audio into an in-memory WAV file and sends it to Groq STT API.
     * Returns Pair<transcript, detectedLanguage>.
     */
    fun transcribeAudio(
        samples: FloatArray,
        language: String? = null
    ): Pair<String, String> {
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

        if (!language.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(GROQ_TRANSCRIPTIONS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Groq STT transcription error: HTTP ${response.code} $err")
                    return Pair("", "")
                }

                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val transcript = json.optString("text", "").trim()
                val detectedLanguage = json.optString("language", "")

                val finalLang = if (detectedLanguage.isNotEmpty()) {
                    detectedLanguage
                } else {
                    LanguageDetector.detectLanguage(transcript).name.lowercase()
                }

                Pair(transcript, finalLang)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq STT transcription failed", e)
            Pair("", "")
        }
    }

    fun cancel() {
        currentEventSource?.cancel()
        currentEventSource = null
    }
}
