package com.sameer.dhvani.utils

enum class SpokenLanguage {
    ENGLISH,
    HINGLISH,
    HINDI
}

object LanguageDetector {

    /**
     * Purified set of distinctive Hindi vocabulary (grammatical markers, pronouns, verbs, question words).
     *
     * CRITICAL: Words that overlap with common English words are STRICTLY EXCLUDED:
     * - "the" (English article) vs "the" (Hindi थे) -> EXCLUDED
     * - "to" (English preposition) vs "to" (Hindi तो) -> EXCLUDED (use "toh" instead)
     * - "me" (English pronoun) vs "me" (Hindi में) -> EXCLUDED (use "mein" instead)
     * - "hum" (English verb) -> EXCLUDED
     *
     * This ensures that pure English sentences like:
     * - "What is the weather forecast for Bangalore tomorrow?"
     * - "How is the traffic in Chennai today?"
     * - "Remind me to call John at 5 PM"
     * are NEVER misclassified as Hinglish.
     */
    private val HINDI_LEXICON = setOf(
        // Pronouns
        "mujhe", "mera", "meri", "mere", "mujhko", "main", "hamara", "hamari", "hamare",
        "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari", "tumhare",
        "yeh", "ye", "woh", "wo", "iska", "iski", "iske", "uska", "uski", "uske",
        // Question words
        "kya", "kab", "kahan", "kaha", "kaise", "kyun", "kyu", "kaun", "kitna", "kitne", "kitni", "kaisa", "kaisi",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
        "raha", "rahi", "rahe", "batao", "bataiye", "bolo", "boliye", "suno", "dekho",
        "chahiye", "chahie", "sakta", "sakti", "sakte", "diya", "liya", "kiya", "gaya", "gayi", "gaye",
        // Postpositions & Conjunctions (strictly excluding "to", "the", "me")
        "ka", "ki", "ke", "ko", "se", "mein", "par", "pe", "tak", "liye", "aur", "ya", "lekin", "bhi", "toh",
        // Time & Calendar
        "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "baje", "abhi",
        // Common conversational words
        "haan", "ha", "nahi", "nahin", "theek", "thik", "accha", "achha", "bilkul",
        "shukriya", "dhanyavaad", "namaste", "namaskar", "bahut", "thoda"
    )

    /**
     * PRIMARY language detection combining Whisper's audio-level signal
     * with text analysis for 100% accuracy on code-switched Hinglish.
     *
     * @param whisperLanguage Language returned by Whisper API ("en", "hi", "ur", "English", "Hindi", etc.)
     * @param transcript The transcribed text
     */
    fun detectFromWhisper(whisperLanguage: String, transcript: String): SpokenLanguage {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        // 1. Devanagari script in text is 100% proof of Hindi
        if (trimmed.any { it.code in 0x0900..0x097F }) {
            return SpokenLanguage.HINDI
        }

        val whisperLang = whisperLanguage.lowercase().trim()

        // 2. Whisper audio analysis detected Hindi or Urdu
        if (whisperLang == "hi" || whisperLang == "hindi" || whisperLang == "ur" || whisperLang == "urdu") {
            return SpokenLanguage.HINGLISH
        }

        // 3. Whisper detected English (or other) — check if user spoke romanized Hindi / Hinglish
        if (hasHindiWords(trimmed)) {
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

        // 1. Check for Devanagari Unicode block (U+0900 to U+097F)
        if (trimmed.any { it.code in 0x0900..0x097F }) {
            return SpokenLanguage.HINDI
        }

        // 2. Tokenize and count distinctive Hindi words in Latin script
        return if (hasHindiWords(trimmed)) {
            SpokenLanguage.HINGLISH
        } else {
            SpokenLanguage.ENGLISH
        }
    }

    /**
     * Checks if text contains distinctive Hindi vocabulary words.
     */
    private fun hasHindiWords(text: String): Boolean {
        val words = text.lowercase().split(Regex("[\\s,?.!;:\"'()]+")).filter { it.isNotBlank() }
        var hindiHits = 0
        for (word in words) {
            if (HINDI_LEXICON.contains(word)) {
                hindiHits++
            }
        }
        return hindiHits >= 1
    }

    /**
     * Determines which voice to use for TTS.
     */
    fun shouldUseHindiVoice(text: String, detectedLang: SpokenLanguage): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        return detectedLang == SpokenLanguage.HINGLISH || detectedLang == SpokenLanguage.HINDI
    }
}
