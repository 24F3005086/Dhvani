package com.sameer.dhvani.benchmark

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import com.sameer.dhvani.utils.LatencyMetrics
import com.sameer.dhvani.utils.SpokenLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight benchmark session logger for Dhvani Voice Agent.
 *
 * Records latency data for each voice interaction trial and persists results
 * to both CSV and JSON files in the device's external Downloads directory.
 *
 * Measurement boundary (E2E):
 *   START = end of user utterance (VAD speech-end) or testUtterance() invocation
 *   END   = first audible audio sample written to AudioTrack (TTS first-audio)
 *
 * All durations use SystemClock.elapsedRealtime() (monotonic, not affected by
 * wall-clock changes) and are derived from actual processing event timestamps,
 * NOT from UI update timing.
 *
 * Stage boundaries:
 *   VAD  = fixed Silero VAD config value (minSilenceDuration) — reported as-is
 *   STT  = speechEnd → sttEnd (Groq Whisper Large V3 cloud round-trip)
 *   LLM  = sttEnd → firstLlmToken (Groq LPU SSE time-to-first-token)
 *   TTS  = firstLlmToken → firstAudioWritten (sentence chunking + on-device synthesis)
 *   E2E  = speechEnd → firstAudioWritten (the user-perceived response latency)
 *
 * NOTE: STT + LLM + TTS will generally approximate E2E, but they are NOT
 * guaranteed to be perfectly additive because:
 *   1. There may be small gaps between stages (coroutine scheduling, GC).
 *   2. TTS synthesis of the first sentence begins at LLM first-token time,
 *      so TTS here includes sentence-chunker buffering + synthesis latency.
 *   The E2E value is the single source of truth for total perceived latency.
 */
class BenchmarkLogger(private val context: Context) {
    companion object {
        private const val TAG = "BenchmarkLogger"
        private const val MAX_TRIALS = 30 // safety cap
    }

    data class TrialRecord(
        val trialNumber: Int,
        val language: String,
        val inputText: String,
        val responseText: String,
        val vadMs: Long,
        val sttMs: Long,
        val llmTtftMs: Long,
        val ttsTtfaMs: Long,
        val totalE2EMs: Long,
        val status: String, // "SUCCESS" or "FAILED:<reason>"
        val wallTimestamp: String, // ISO 8601 wall-clock timestamp
        val isTestChip: Boolean // true = testUtterance(), false = real voice
    )

    private val trials = CopyOnWriteArrayList<TrialRecord>()

    @Volatile
    var isEnabled = true
        private set

    val trialCount: Int get() = trials.size

    /**
     * Enable benchmark recording mode. Clears any previous session data.
     */
    fun startSession() {
        trials.clear()
        isEnabled = true
        Log.i(TAG, "Benchmark session started")
    }

    /**
     * Disable benchmark recording and persist results.
     */
    fun endSession() {
        isEnabled = false
        persistResults()
        Log.i(TAG, "Benchmark session ended with ${trials.size} trials")
    }

    /**
     * Record a completed trial from the pipeline's LatencyMetrics.
     */
    fun recordTrial(
        inputText: String,
        responseText: String,
        language: SpokenLanguage,
        metrics: LatencyMetrics,
        isTestChip: Boolean,
        status: String = "SUCCESS"
    ) {
        if (!isEnabled) return
        if (trials.size >= MAX_TRIALS) {
            Log.w(TAG, "Max trial cap ($MAX_TRIALS) reached, ignoring")
            return
        }

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        isoFormatter.timeZone = TimeZone.getDefault()

        val record = TrialRecord(
            trialNumber = trials.size + 1,
            language = when (language) {
                SpokenLanguage.ENGLISH -> "English"
                SpokenLanguage.HINGLISH -> "Hinglish"
                SpokenLanguage.HINDI -> "Hindi"
            },
            inputText = inputText,
            responseText = responseText.take(200), // truncate for storage
            vadMs = metrics.vadDurationMs,
            sttMs = metrics.sttDurationMs,
            llmTtftMs = metrics.llmTtftMs,
            ttsTtfaMs = metrics.ttsTtfaMs,
            totalE2EMs = metrics.totalE2EMs,
            status = status,
            wallTimestamp = isoFormatter.format(Date()),
            isTestChip = isTestChip
        )

        trials.add(record)
        Log.i(TAG, "Trial #${record.trialNumber} recorded: E2E=${record.totalE2EMs}ms | " +
                "STT=${record.sttMs}ms | LLM=${record.llmTtftMs}ms | TTS=${record.ttsTtfaMs}ms | " +
                "${record.language} | ${record.status}")

        // Auto-persist after each trial for crash safety
        persistResults()
    }

    /**
     * Record a failed trial (e.g. blank transcript, LLM error).
     */
    fun recordFailure(
        inputText: String,
        language: SpokenLanguage,
        reason: String,
        isTestChip: Boolean
    ) {
        if (!isEnabled) return

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        isoFormatter.timeZone = TimeZone.getDefault()

        val record = TrialRecord(
            trialNumber = trials.size + 1,
            language = when (language) {
                SpokenLanguage.ENGLISH -> "English"
                SpokenLanguage.HINGLISH -> "Hinglish"
                SpokenLanguage.HINDI -> "Hindi"
            },
            inputText = inputText,
            responseText = "",
            vadMs = 0,
            sttMs = 0,
            llmTtftMs = 0,
            ttsTtfaMs = 0,
            totalE2EMs = 0,
            status = "FAILED:$reason",
            wallTimestamp = isoFormatter.format(Date()),
            isTestChip = isTestChip
        )

        trials.add(record)
        Log.w(TAG, "Trial #${record.trialNumber} FAILED: $reason")
        persistResults()
    }

    /**
     * Write results to CSV and JSON files in multiple safe locations:
     * 1. Public Downloads: /sdcard/Download/dhvani_benchmark/
     * 2. App External Files: /sdcard/Android/data/com.sameer.dhvani/files/dhvani_benchmark/
     * 3. App Internal Files: /data/user/0/com.sameer.dhvani/files/dhvani_benchmark/
     */
    private fun persistResults() {
        val targets = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "dhvani_benchmark"),
            context.getExternalFilesDir(null)?.let { File(it, "dhvani_benchmark") },
            File(context.filesDir, "dhvani_benchmark")
        )

        for (dir in targets) {
            try {
                dir.mkdirs()
                writeCSV(File(dir, "dhvani_latency_results.csv"))
                writeJSON(File(dir, "dhvani_latency_results.json"))
                Log.d(TAG, "Benchmark results persisted to ${dir.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not write to ${dir.absolutePath}: ${e.message}")
            }
        }

        // Also emit structured log line for easy extraction via ADB logcat
        val last = trials.lastOrNull()
        if (last != null) {
            Log.i("DhvaniBenchmark", "TRIAL_RESULT: trial=${last.trialNumber}, lang=${last.language}, " +
                    "vad=${last.vadMs}, stt=${last.sttMs}, llm=${last.llmTtftMs}, tts=${last.ttsTtfaMs}, " +
                    "e2e=${last.totalE2EMs}, status=${last.status}, input=\"${last.inputText}\"")
        }
    }

    private fun writeCSV(file: File) {
        val header = "trial,language,input_text,response_text,vad_ms,stt_ms,llm_ttft_ms," +
                "tts_ttfa_ms,e2e_ms,status,timestamp,is_test_chip"

        val rows = trials.joinToString("\n") { t ->
            listOf(
                t.trialNumber.toString(),
                t.language,
                "\"${t.inputText.replace("\"", "\\\"")}\"",
                "\"${t.responseText.replace("\"", "\\\"")}\"",
                t.vadMs.toString(),
                t.sttMs.toString(),
                t.llmTtftMs.toString(),
                t.ttsTtfaMs.toString(),
                t.totalE2EMs.toString(),
                t.status,
                t.wallTimestamp,
                t.isTestChip.toString()
            ).joinToString(",")
        }

        file.writeText("$header\n$rows\n")
    }

    private fun writeJSON(file: File) {
        val successfulTrials = trials.filter { it.status == "SUCCESS" }
        val failedTrials = trials.filter { it.status != "SUCCESS" }

        val e2eValues = successfulTrials.map { it.totalE2EMs }.sorted()

        val root = JSONObject().apply {
            put("metadata", JSONObject().apply {
                put("app", "Dhvani Voice Agent")
                put("version", "1.0")
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("android_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                put("soc", Build.HARDWARE)
                put("measurement_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                put("measurement_boundary", JSONObject().apply {
                    put("start", "VAD speech-end (end of user utterance)")
                    put("end", "TTS first-audio (first audible sample written to AudioTrack)")
                    put("clock", "SystemClock.elapsedRealtime() — monotonic, not wall-clock")
                })
                put("pipeline", JSONObject().apply {
                    put("vad", "Silero VAD (ONNX, on-device)")
                    put("stt", "Groq Whisper Large V3 Turbo (cloud)")
                    put("llm", "Groq LPU — Qwen 3.8 27B (cloud, SSE streaming)")
                    put("tts_english", "Piper VITS en_US-amy-medium (on-device)")
                    put("tts_hindi", "Supertonic 3 Hindi flow-matching diffusion (on-device, numSteps=3)")
                })
                put("notes", JSONArray().apply {
                    put("E2E latency = speechEnd → firstAudioWritten (user-perceived response time)")
                    put("STT + LLM + TTS may not sum exactly to E2E due to coroutine scheduling overhead")
                    put("VAD duration is the configured Silero minSilenceDuration (450ms), not a per-trial measurement")
                    put("Hindi/Hinglish TTS latency is optimized with 3-step progressive flow-matching diffusion")
                    put("Results reflect this specific device, network, and test session only")
                })
            })

            put("summary", JSONObject().apply {
                put("total_trials", trials.size)
                put("successful_trials", successfulTrials.size)
                put("failed_trials", failedTrials.size)
                if (e2eValues.isNotEmpty()) {
                    put("mean_e2e_ms", e2eValues.average().toLong())
                    put("median_e2e_ms", percentile(e2eValues, 50))
                    put("min_e2e_ms", e2eValues.first())
                    put("max_e2e_ms", e2eValues.last())
                    put("p95_e2e_ms", percentile(e2eValues, 95))
                    put("p95_note", "p95 calculated via nearest-rank method on ${e2eValues.size} observations")

                    val sttValues = successfulTrials.map { it.sttMs }.sorted()
                    put("mean_stt_ms", sttValues.average().toLong())
                    put("median_stt_ms", percentile(sttValues, 50))

                    val llmValues = successfulTrials.map { it.llmTtftMs }.sorted()
                    put("mean_llm_ttft_ms", llmValues.average().toLong())
                    put("median_llm_ttft_ms", percentile(llmValues, 50))

                    val ttsValues = successfulTrials.map { it.ttsTtfaMs }.sorted()
                    put("mean_tts_ttfa_ms", ttsValues.average().toLong())
                    put("median_tts_ttfa_ms", percentile(ttsValues, 50))
                }
            })

            put("trials", JSONArray().apply {
                for (t in trials) {
                    put(JSONObject().apply {
                        put("trial", t.trialNumber)
                        put("language", t.language)
                        put("input", t.inputText)
                        put("response", t.responseText)
                        put("vad_ms", t.vadMs)
                        put("stt_ms", t.sttMs)
                        put("llm_ttft_ms", t.llmTtftMs)
                        put("tts_ttfa_ms", t.ttsTtfaMs)
                        put("e2e_ms", t.totalE2EMs)
                        put("status", t.status)
                        put("timestamp", t.wallTimestamp)
                        put("is_test_chip", t.isTestChip)
                    })
                }
            })
        }

        file.writeText(root.toString(2))
    }

    /**
     * Nearest-rank percentile calculation.
     * For small samples (n < 20), this returns the ceil(p/100 * n)-th smallest value.
     */
    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /**
     * Get current trial results as a list (for UI display).
     */
    fun getTrials(): List<TrialRecord> = trials.toList()

    /**
     * Get the output directory path for pulling files via ADB.
     */
    fun getOutputPath(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        return File(downloadsDir, "dhvani_benchmark").absolutePath
    }
}
