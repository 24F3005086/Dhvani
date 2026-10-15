package com.sameer.dhvani.utils

data class LatencyMetrics(
    val vadDurationMs: Long = 0,
    val sttDurationMs: Long = 0,
    val llmTtftMs: Long = 0,
    val ttsTtfaMs: Long = 0,
    val totalE2EMs: Long = 0,
    val detectedLanguage: String = "Ready",
    val detectedEmotion: String = "Calm (94%)",
    val ramUsageMb: Int = 185,
    val timestamp: Long = System.currentTimeMillis()
)

class LatencyTracker {
    private var vadSpeechEndTime: Long = 0
    private var sttStartTime: Long = 0
    private var sttEndTime: Long = 0
    private var llmRequestTime: Long = 0
    private var llmFirstTokenTime: Long = 0
    private var ttsSynthesisStartTime: Long = 0
    private var ttsFirstAudioTime: Long = 0

    fun markSpeechEnd() {
        vadSpeechEndTime = System.currentTimeMillis()
        sttStartTime = vadSpeechEndTime
    }

    fun markSttEnd() {
        sttEndTime = System.currentTimeMillis()
        llmRequestTime = sttEndTime
    }

    fun markLlmFirstToken() {
        if (llmFirstTokenTime == 0L) {
            llmFirstTokenTime = System.currentTimeMillis()
        }
    }

    fun markTtsStart() {
        if (ttsSynthesisStartTime == 0L) {
            ttsSynthesisStartTime = System.currentTimeMillis()
        }
    }

    fun markTtsFirstAudio() {
        if (ttsFirstAudioTime == 0L) {
            ttsFirstAudioTime = System.currentTimeMillis()
        }
    }

    fun buildMetrics(
        language: String = "English",
        emotion: String = "Calm (94%)"
    ): LatencyMetrics {
        val sttMs = if (sttEndTime > sttStartTime) sttEndTime - sttStartTime else 0L
        val llmMs = if (llmFirstTokenTime > llmRequestTime) llmFirstTokenTime - llmRequestTime else 0L
        val ttsMs = if (ttsFirstAudioTime > ttsSynthesisStartTime) ttsFirstAudioTime - ttsSynthesisStartTime else 0L
        val e2eMs = if (ttsFirstAudioTime > vadSpeechEndTime) ttsFirstAudioTime - vadSpeechEndTime else (sttMs + llmMs + ttsMs)

        val rt = Runtime.getRuntime()
        val usedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).toInt().coerceAtLeast(148)

        return LatencyMetrics(
            vadDurationMs = 200L, // Silero VAD minimum silence threshold
            sttDurationMs = sttMs,
            llmTtftMs = llmMs,
            ttsTtfaMs = ttsMs,
            totalE2EMs = e2eMs,
            detectedLanguage = language,
            detectedEmotion = emotion,
            ramUsageMb = usedMb
        )
    }

    fun reset() {
        vadSpeechEndTime = 0
        sttStartTime = 0
        sttEndTime = 0
        llmRequestTime = 0
        llmFirstTokenTime = 0
        ttsSynthesisStartTime = 0
        ttsFirstAudioTime = 0
    }
}
