package com.sameer.dhvani.utils

enum class SpokenLanguage {
    ENGLISH,
    HINGLISH,
    HINDI
}

object LanguageDetector {

    /**
     * Strict set of distinctive Hindi vocabulary (grammatical markers, pronouns, verbs, question words).
     * NOTE: Common English words used in Hinglish contexts (like "weather", "meeting", "alarm", "schedule", "reminder")
     * are strictly EXCLUDED here so pure English queries are never misclassified as Hinglish.
     */
    private val HINDI_LEXICON = setOf(
        // Pronouns
        "mujhe", "mera", "meri", "mere", "mujhko", "main", "hum", "hamara", "hamari", "hamare",
        "aap", "aapka", "aapki", "aapke", "tum", "tumhara", "tumhari", "tumhare",
        "yeh", "ye", "woh", "wo", "iska", "iski", "iske", "uska", "uski", "uske",
        // Question words
        "kya", "kab", "kahan", "kaha", "kaise", "kyun", "kyu", "kaun", "kitna", "kitne", "kitni", "kaisa", "kaisi",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "the", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
        "raha", "rahi", "rahe", "batao", "bataiye", "bolo", "boliye", "suno", "dekho",
        "chahiye", "chahie", "sakta", "sakti", "sakte", "diya", "liya", "kiya", "gaya", "gayi", "gaye",
        // Postpositions & Conjunctions
        "ka", "ki", "ke", "ko", "se", "me", "mein", "par", "pe", "tak", "liye", "aur", "ya", "lekin", "bhi", "toh", "to",
        // Time & Calendar
        "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "baje", "abhi",
        // Common conversational words
        "haan", "ha", "nahi", "nahin", "theek", "thik", "accha", "achha", "bilkul",
        "shukriya", "dhanyavaad", "namaste", "namaskar", "bahut", "thoda"
    )

    /**
     * Accurately detects whether a given transcript is English or Hinglish/Hindi.
     */
    fun detectLanguage(text: String): SpokenLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        // 1. Check for Devanagari Unicode block (U+0900 to U+097F)
        val hasDevanagari = trimmed.any { it.code in 0x0900..0x097F }
        if (hasDevanagari) {
            return SpokenLanguage.HINDI
        }

        // 2. Tokenize and count distinctive Hindi words in Latin script
        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()]+")).filter { it.isNotBlank() }
        var hindiHits = 0
        for (word in words) {
            if (HINDI_LEXICON.contains(word)) {
                hindiHits++
            }
        }

        // If any distinctive Hindi words appear, it is Hinglish; otherwise pure English
        return if (hindiHits >= 1) {
            SpokenLanguage.HINGLISH
        } else {
            SpokenLanguage.ENGLISH
        }
    }

    /**
     * Determines which Piper TTS model voice to use.
     * Devanagari text or Hinglish/Hindi speech routes to Hindi voice (Rohan).
     * Pure English routes to English voice (Amy).
     */
    fun shouldUseHindiVoice(text: String, detectedLang: SpokenLanguage): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        return detectedLang == SpokenLanguage.HINGLISH || detectedLang == SpokenLanguage.HINDI
    }
}
