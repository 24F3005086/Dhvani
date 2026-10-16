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
     * Strictly excludes words that overlap with common English words.
     */
    private val UNAMBIGUOUS_HINDI_LEXICON = setOf(
        // Pronouns
        "mujhe", "mujhko", "mera", "meri", "mere", "hamara", "hamari", "hamare",
        "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari", "tumhare",
        "iska", "iski", "iske", "uska", "uski", "uske", "unka", "unki", "unke",
        "hum", "humko", "humne", "maine", "inhone", "unhone",
        // Question words
        "kya", "kab", "kahan", "kaha", "kaise", "kyun", "kyu", "kaun", "kitna", "kitne", "kitni", "kaisa", "kaisi",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "the", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
        "raha", "rahi", "rahe", "batao", "bataiye", "bolo", "boliye", "suno", "dekho",
        "chahiye", "chahie", "sakta", "sakti", "sakte", "diya", "liya", "kiya", "gaya", "gayi", "gaye",
        "hota", "hoti", "hote", "jao", "jaana", "aao", "aana", "ruk", "ruko",
        // Time & Calendar
        "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "baje", "abhi",
        // Common conversational words
        "haan", "nahi", "nahin", "theek", "thik", "accha", "achha", "bilkul", "yaar",
        "shukriya", "dhanyavaad", "namaste", "namaskar", "bahut", "thoda", "kripya", "samay",
        "zaroor", "zarur", "pehle", "baad", "saath", "andar", "bahar"
    )

    /**
     * Comprehensive English vocabulary (including question, daily, and mathematical words).
     */
    private val COMMON_ENGLISH_LEXICON = setOf(
        // Question words
        "what", "is", "the", "how", "are", "why", "when", "where", "who", "which",
        "whose", "whom", "can", "could", "would", "should", "will", "shall", "do", "does", "did",
        // Pronouns & Articles
        "you", "your", "yours", "me", "my", "mine", "i", "we", "our", "ours",
        "he", "she", "it", "they", "them", "their", "a", "an", "this", "that", "these", "those",
        // Action & Math verbs
        "set", "tell", "help", "calculate", "multiply", "multiplied", "divide", "divided",
        "times", "into", "plus", "minus", "equals", "equal", "sum", "product", "difference",
        "solve", "count", "convert", "find", "give", "make", "take", "show", "open", "close",
        // Common domains
        "reminder", "alarm", "meeting", "weather", "forecast", "temperature", "bangalore",
        "tomorrow", "today", "yesterday", "morning", "afternoon", "evening", "night",
        "schedule", "next", "time", "clock", "traffic", "flight", "train", "car", "route",
        "hello", "hi", "hey", "please", "thanks", "thank", "good", "fine", "okay",
        "question", "answer", "about", "with", "without", "from", "for", "in", "on", "at",
        "numbers", "number", "zero", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
        "eighty", "ninety", "hundred", "thousand", "am", "pm", "celsius", "degrees"
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
            Log.d(TAG, "Devanagari characters detected in transcript -> HINDI")
            return SpokenLanguage.HINDI
        }

        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()]+")).filter { it.isNotBlank() }
        var englishHits = 0
        var hindiHits = 0

        for (word in words) {
            if (UNAMBIGUOUS_HINDI_LEXICON.contains(word)) hindiHits++
            if (COMMON_ENGLISH_LEXICON.contains(word)) englishHits++
        }

        Log.d(TAG, "LID Analysis: words=${words.size}, enHits=$englishHits, hiHits=$hindiHits, whisperLang='$whisperLanguage', text='$trimmed'")

        // 2. ZERO Hindi markers present
        if (hindiHits == 0) {
            // Check for English math, digits, or English words
            val hasDigits = trimmed.any { it.isDigit() }
            val hasMathSymbols = trimmed.contains(Regex("[+*×÷=/-]"))
            if (englishHits > 0 || hasDigits || hasMathSymbols) {
                Log.d(TAG, "Pure English / Math query ($englishHits enHits, 0 hiHits) -> ENGLISH")
                return SpokenLanguage.ENGLISH
            }
        }

        // 3. Clear Hindi / Hinglish markers present
        if (hindiHits >= 2) {
            Log.d(TAG, "Multiple Hindi markers ($hindiHits hits) -> HINGLISH")
            return SpokenLanguage.HINGLISH
        }

        if (hindiHits == 1) {
            // If English vocabulary dominates significantly, treat as English
            return if (englishHits >= 2) {
                Log.d(TAG, "English dominant ($englishHits enHits vs 1 hiHit) -> ENGLISH")
                SpokenLanguage.ENGLISH
            } else {
                Log.d(TAG, "Distinct Hindi marker with low English -> HINGLISH")
                SpokenLanguage.HINGLISH
            }
        }

        // 4. Acoustic fallback only when neither text lexicon triggered
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

        if (hindiHits == 0) {
            val hasDigits = trimmed.any { it.isDigit() }
            val hasMathSymbols = trimmed.contains(Regex("[+*×÷=/-]"))
            if (englishHits > 0 || hasDigits || hasMathSymbols) {
                return SpokenLanguage.ENGLISH
            }
        }

        return if (hindiHits >= 1) {
            if (englishHits >= 2 && hindiHits == 1) SpokenLanguage.ENGLISH else SpokenLanguage.HINGLISH
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
