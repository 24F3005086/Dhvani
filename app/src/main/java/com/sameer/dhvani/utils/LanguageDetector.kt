package com.sameer.dhvani.utils

enum class SpokenLanguage {
    ENGLISH,
    HINGLISH,
    HINDI
}

object LanguageDetector {

    // Common Hinglish words (Latin script)
    private val HINGLISH_KEYWORDS = setOf(
        "kya", "hai", "hain", "mujhe", "mera", "meri", "mere", "aaj", "kal",
        "ka", "ki", "ke", "ko", "se", "aur", "karo", "karna", "baje", "subah",
        "shaam", "raat", "kab", "kahan", "kaise", "batao", "bataiye", "bolo",
        "haan", "nahi", "theek", "shukriya", "dhanyavaad", "namaste", "schedule",
        "meeting", "alarm", "weather", "reminder"
    )

    fun detectLanguage(text: String): SpokenLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        // 1. Check for Devanagari Unicode block (U+0900 to U+097F)
        val hasDevanagari = trimmed.any { it.code in 0x0900..0x097F }
        if (hasDevanagari) {
            return SpokenLanguage.HINDI
        }

        // 2. Check for Hinglish code-switching in Latin script
        val words = trimmed.lowercase().split(Regex("[\\s,?.!]+"))
        var hinglishHits = 0
        for (word in words) {
            if (HINGLISH_KEYWORDS.contains(word)) {
                hinglishHits++
            }
        }

        return if (hinglishHits >= 1) {
            SpokenLanguage.HINGLISH
        } else {
            SpokenLanguage.ENGLISH
        }
    }

    /**
     * Determines which Piper TTS model voice to use.
     * If text contains Devanagari or is marked as Hinglish/Hindi, prefer Hindi voice (rohan).
     * Else English voice (amy).
     */
    fun shouldUseHindiVoice(text: String, detectedLang: SpokenLanguage): Boolean {
        // If the text contains any Devanagari characters, definitely use Hindi voice
        if (text.any { it.code in 0x0900..0x097F }) return true

        // If user query was Hinglish or Hindi, use Hindi voice which phonemizes Hinglish naturally
        return detectedLang == SpokenLanguage.HINGLISH || detectedLang == SpokenLanguage.HINDI
    }
}
