package com.sameer.dhvani.utils

import android.util.Log

enum class SpokenLanguage {
    ENGLISH,
    HINGLISH,
    HINDI
}

object LanguageDetector {
    private const val TAG = "LanguageDetector"

    /**
     * Unambiguous distinctive Hindi vocabulary (grammatical markers, pronouns, verbs, question words).
     *
     * CRITICAL: Words that overlap with common English words are STRICTLY EXCLUDED:
     * - "main" (English adjective: main road, main idea) vs Hindi मैं -> EXCLUDED
     * - "par" (English noun: on par) vs Hindi पर -> EXCLUDED
     * - "ye" (English slang / archaic: ye) vs Hindi ये -> EXCLUDED
     * - "wo" (English interjection: whoa/wo) vs Hindi वो -> EXCLUDED
     * - "the" (English article) vs Hindi थे -> EXCLUDED
     * - "to" (English preposition) vs Hindi तो -> EXCLUDED
     * - "me" (English pronoun) vs Hindi में -> EXCLUDED
     * - "bhi" -> EXCLUDED
     * - Single-letter/two-letter ambiguous particles: "ka", "ki", "ke", "ko", "se" -> EXCLUDED
     */
    private val UNAMBIGUOUS_HINDI_LEXICON = setOf(
        // Pronouns
        "mujhe", "mujhko", "mera", "meri", "mere", "hamara", "hamari", "hamare",
        "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari", "tumhare",
        "iska", "iski", "iske", "uska", "uski", "uske",
        // Question words
        "kya", "kab", "kahan", "kaha", "kaise", "kyun", "kyu", "kaun", "kitna", "kitne", "kitni", "kaisa", "kaisi",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
        "raha", "rahi", "rahe", "batao", "bataiye", "bolo", "boliye", "suno", "dekho",
        "chahiye", "chahie", "sakta", "sakti", "sakte", "diya", "liya", "kiya", "gaya", "gayi", "gaye",
        // Time & Calendar
        "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "baje", "abhi",
        // Common conversational words
        "haan", "nahi", "nahin", "theek", "thik", "accha", "achha", "bilkul",
        "shukriya", "dhanyavaad", "namaste", "namaskar", "bahut", "thoda", "kripya", "samay"
    )

    /**
     * Common English vocabulary to reliably identify English sentences
     * even when Whisper audio classifier mislabels Indian English accents as "hi".
     */
    private val COMMON_ENGLISH_LEXICON = setOf(
        "what", "is", "the", "can", "you", "set", "a", "reminder", "for", "my",
        "meeting", "at", "weather", "forecast", "bangalore", "tomorrow", "how",
        "are", "why", "tell", "me", "hello", "hi", "please", "help", "schedule",
        "next", "time", "today", "would", "like", "to", "could", "thanks", "thank",
        "good", "morning", "afternoon", "evening", "doing", "about", "do", "does",
        "did", "will", "shall", "have", "has", "had", "where", "when", "who", "which",
        "there", "here", "this", "that", "an", "in", "on", "off", "with", "without",
        "from", "of", "and", "or", "but", "not", "no", "yes", "if", "so", "because",
        "just", "want", "need", "know", "think", "see", "get", "got", "make", "give",
        "take", "go", "come", "alarm", "clock", "traffic", "route", "day", "night",
        "main", "feature", "agent", "app", "model", "phone", "call", "send", "message"
    )

    /**
     * Cross-verifies Whisper's acoustic language detection with transcript token analysis.
     * Prevents Indian-accented English from being misrouted to Hindi,
     * while accurately preserving code-mixed Hinglish.
     */
    fun detectFromWhisper(whisperLanguage: String, transcript: String): SpokenLanguage {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        // 1. Devanagari script in text is 100% proof of Hindi
        if (trimmed.any { it.code in 0x0900..0x097F }) {
            Log.d(TAG, "Devanagari characters detected -> HINDI")
            return SpokenLanguage.HINDI
        }

        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()]+")).filter { it.isNotBlank() }
        var englishHits = 0
        var hindiHits = 0

        for (word in words) {
            if (UNAMBIGUOUS_HINDI_LEXICON.contains(word)) hindiHits++
            if (COMMON_ENGLISH_LEXICON.contains(word)) englishHits++
        }

        Log.d(TAG, "LID Analysis: words=${words.size}, enHits=$englishHits, hiHits=$hindiHits, whisperLang='$whisperLanguage'")

        // 2. Strong English presence with ZERO Hindi markers -> ALWAYS English
        // (Even if Whisper's acoustic model guessed "hi" because of an Indian accent!)
        if (englishHits >= 2 && hindiHits == 0) {
            Log.d(TAG, "Strong English vocabulary ($englishHits hits) with 0 Hindi words -> OVERRIDING to ENGLISH")
            return SpokenLanguage.ENGLISH
        }

        // 3. Clear Hindi / Hinglish markers present
        if (hindiHits >= 2) {
            Log.d(TAG, "Multiple Hindi markers ($hindiHits hits) -> HINGLISH")
            return SpokenLanguage.HINGLISH
        }

        if (hindiHits == 1) {
            // If it's mostly English words with 1 borderline word, favor English
            return if (englishHits >= 3) {
                Log.d(TAG, "Dominantly English ($englishHits hits vs 1 Hindi) -> ENGLISH")
                SpokenLanguage.ENGLISH
            } else {
                Log.d(TAG, "Single distinct Hindi marker with low English -> HINGLISH")
                SpokenLanguage.HINGLISH
            }
        }

        // 4. No strong text markers on either side: fall back to Whisper acoustic prediction
        val whisperLang = whisperLanguage.lowercase().trim()
        if (whisperLang == "hi" || whisperLang == "hindi" || whisperLang == "ur" || whisperLang == "urdu") {
            Log.d(TAG, "Acoustic fallback: Whisper detected Hindi/Urdu -> HINGLISH")
            return SpokenLanguage.HINGLISH
        }

        return SpokenLanguage.ENGLISH
    }

    /**
     * Text-only language detection (used for test chips and offline fallback).
     */
    fun detectLanguage(text: String): SpokenLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        if (trimmed.any { it.code in 0x0900..0x097F }) {
            return SpokenLanguage.HINDI
        }

        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()]+")).filter { it.isNotBlank() }
        var englishHits = 0
        var hindiHits = 0

        for (word in words) {
            if (UNAMBIGUOUS_HINDI_LEXICON.contains(word)) hindiHits++
            if (COMMON_ENGLISH_LEXICON.contains(word)) englishHits++
        }

        if (englishHits >= 2 && hindiHits == 0) {
            return SpokenLanguage.ENGLISH
        }

        return if (hindiHits >= 1) {
            if (englishHits >= 3 && hindiHits == 1) SpokenLanguage.ENGLISH else SpokenLanguage.HINGLISH
        } else {
            SpokenLanguage.ENGLISH
        }
    }

    /**
     * Determines whether to use Hindi voice based on text script and detected language.
     */
    fun shouldUseHindiVoice(text: String, detectedLang: SpokenLanguage): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        return detectedLang == SpokenLanguage.HINGLISH || detectedLang == SpokenLanguage.HINDI
    }
}
