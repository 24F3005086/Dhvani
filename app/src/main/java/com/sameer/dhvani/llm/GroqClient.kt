package com.sameer.dhvani.llm

import android.util.Log
import com.sameer.dhvani.BuildConfig
import com.sameer.dhvani.utils.SpokenLanguage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqClient(
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL_PRIMARY = "qwen/qwen3.8-27b"
        private const val MODEL_FALLBACK = "openai/gpt-oss-20b"
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
        onToken: (String) -> Unit,
        onComplete: (fullResponse: String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        cancel()

        val systemPrompt = when (detectedLanguage) {
            SpokenLanguage.HINGLISH, SpokenLanguage.HINDI ->
                "You are Dhvani (ध्वनि), an ultra-fast on-device voice assistant for IISc Bangalore. " +
                "The user spoke in Hindi or Hinglish. Respond in natural, conversational Hinglish (Hindi written in Roman/Latin script, or simple Devanagari). " +
                "Keep responses concise (1 to 2 spoken sentences maximum). Do not use bullet points, bold text, or markdown."
            SpokenLanguage.ENGLISH ->
                "You are Dhvani, an ultra-fast on-device voice assistant for IISc Bangalore. " +
                "Respond in clear, natural, conversational English. " +
                "Keep responses concise (1 to 2 spoken sentences maximum). Do not use bullet points, bold text, or markdown."
        }

        val requestPayload = JSONObject().apply {
            put("model", MODEL_PRIMARY)
            put("stream", true)
            put("temperature", 0.6)
            put("max_tokens", 200)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
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
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Groq SSE connection established")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    onComplete(fullResponseBuilder.toString().trim())
                    return
                }

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val token = delta?.optString("content", "") ?: ""
                        if (token.isNotEmpty()) {
                            fullResponseBuilder.append(token)
                            onToken(token)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE chunk: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                val responseText = fullResponseBuilder.toString().trim()
                if (responseText.isNotEmpty()) {
                    onComplete(responseText)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Groq stream failure. Response code: ${response?.code}", t)
                onError(t ?: Exception("Groq HTTP error: ${response?.code}"))
            }
        }

        currentEventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    fun cancel() {
        currentEventSource?.cancel()
        currentEventSource = null
    }
}
