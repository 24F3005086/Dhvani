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
        // Postpositions & Case Markers
        "ka", "ki", "ke", "ko", "se", "mein", "me", "par", "pe",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "the", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
        "raha", "rahi", "rahe", "bata", "batao", "bataiye", "bol", "bolo", "boliye", "suno", "dekho",
        "chahiye", "chahie", "sakta", "sakti", "sakte", "diya", "liya", "kiya", "gaya", "gayi", "gaye",
        "hota", "hoti", "hote", "jao", "jaana", "aao", "aana", "ruk", "ruko",
        "laga", "lagao", "lagado", "lagana", "lagaye", "lagaiye", "de", "dena", "dijiye", "le", "lena", "lijiye",
        "kholo", "chalao", "band",
        // Time & Calendar
        "aaj", "kal", "parso", "subah", "shaam", "raat", "dopahar", "baje", "abhi",
        // Common conversational words
        "haan", "nahi", "nahin", "theek", "thik", "accha", "achha", "bilkul", "yaar", "bhai",
        "shukriya", "dhanyavaad", "namaste", "namaskar", "bahut", "thoda", "kripya", "samay",
        "zaroor", "zarur", "pehle", "baad", "saath", "andar", "bahar", "chai", "peeni", "peena", "khana"
    )

    /**
     * Comprehensive English vocabulary (including question, daily, and mathematical words).
     * Note: "do" is intentionally excluded because it collides with Hindi "दो" (imperative verb / number 2).
     */
    private val COMMON_ENGLISH_LEXICON = setOf(
        // Question words
        "what", "is", "the", "how", "are", "why", "when", "where", "who", "which",
        "whose", "whom", "can", "could", "would", "should", "will", "shall", "does", "did",
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
     * Common English loanwords transcribed into Devanagari by Whisper.
     * When any of these appear in a Devanagari transcript, the user was speaking Hinglish.
     */
    private val DEVANAGARI_HINGLISH_LOANWORDS = setOf(
        "कैंपस", "नियरेस्ट", "कैंटीन", "अलार्म", "सेट", "वेदर", "टेंपरेचर",
        "मीटिंग", "शेड्यूल", "रिमाइंडर", "टाइम", "कॉलेज", "प्रोजेक्ट", "ऑफिस",
        "कैलकुलेट", "मल्टीप्लाई", "डिवाइड", "कॉल", "मैसेज", "फोन", "बस",
        "ट्रेन", "टिकट", "हेल्प", "प्रॉब्लम", "डिपार्टमेंट", "लैब", "प्रोफेसर",
        "क्लास", "स्टूडेंट", "टिप्स", "डॉक्टर", "हॉस्पिटल", "डिग्री", "ट्रैफिक",
        "रूटीन", "चेक", "मॉर्निंग", "इवनिंग", "नाईट", "स्टार्ट", "स्टॉप"
    )

    /**
     * Cross-verifies Whisper's acoustic language detection with transcript token analysis.
     * Accurately routes to ENGLISH, HINGLISH, or pure HINDI.
     */
    fun detectFromWhisper(whisperLanguage: String, transcript: String): SpokenLanguage {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return SpokenLanguage.ENGLISH

        val wLang = whisperLanguage.lowercase().trim()
        val hasDevanagari = trimmed.any { it.code in 0x0900..0x097F }
        val hasLatin = trimmed.any { (it in 'a'..'z') || (it in 'A'..'Z') }

        // Tokenize
        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()|।]+")).filter { it.isNotBlank() }
        val hindiHits = words.count { UNAMBIGUOUS_HINDI_LEXICON.contains(it) }
        val englishHits = words.count { COMMON_ENGLISH_LEXICON.contains(it) }
        val loanwordHits = words.count { DEVANAGARI_HINGLISH_LOANWORDS.contains(it) }

        Log.d(TAG, "LID Analysis: words=${words.size}, enHits=$englishHits, hiHits=$hindiHits, loanHits=$loanwordHits, whisperLang='$whisperLanguage', text='$trimmed'")

        // 1. Whisper detected English (or acoustic model thinks English)
        if (wLang == "en" || wLang == "english") {
            // Did user speak Hinglish in Roman script? (e.g. "500 rupaye kharch ho gaye kitne bache")
            if (hindiHits >= 1 && (hindiHits >= englishHits || englishHits <= 1)) {
                Log.d(TAG, "Whisper English with distinct Hindi markers ($hindiHits hiHits) -> HINGLISH")
                return SpokenLanguage.HINGLISH
            }
            // If transcript contains Devanagari with loanwords, it's Hinglish
            if (hasDevanagari && loanwordHits > 0) {
                Log.d(TAG, "Whisper English with Devanagari loanwords -> HINGLISH")
                return SpokenLanguage.HINGLISH
            }
            return SpokenLanguage.ENGLISH
        }

        // 2. Whisper detected Hindi or Urdu
        if (wLang == "hi" || wLang == "hindi" || wLang == "ur" || wLang == "urdu") {
            // If mixed with Latin letters -> HINGLISH
            if (hasLatin) {
                Log.d(TAG, "Whisper Hindi with Latin script tokens -> HINGLISH")
                return SpokenLanguage.HINGLISH
            }
            // If contains English loanwords in Devanagari ("कैंपस", "अलार्म", "कैंटीन", etc.) -> HINGLISH
            if (loanwordHits > 0) {
                Log.d(TAG, "Whisper Hindi with English loanwords ($loanwordHits hits) -> HINGLISH")
                return SpokenLanguage.HINGLISH
            }
            // Otherwise, pure Hindi! ("कल बैंगलोर का मौसम कैसा रहेगा?", "भारत के प्रधानमंत्री कौन हैं?")
            Log.d(TAG, "Whisper Hindi without English loanwords -> HINDI")
            return SpokenLanguage.HINDI
        }

        // 3. Fallback when acoustic language is unknown
        if (hasDevanagari && !hasLatin) {
            return if (loanwordHits > 0) SpokenLanguage.HINGLISH else SpokenLanguage.HINDI
        }
        if (hasDevanagari && hasLatin) {
            return SpokenLanguage.HINGLISH
        }
        if (hindiHits >= 1) {
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

        val hasDevanagari = trimmed.any { it.code in 0x0900..0x097F }
        val hasLatin = trimmed.any { (it in 'a'..'z') || (it in 'A'..'Z') }

        if (hasDevanagari && !hasLatin) {
            return SpokenLanguage.HINDI
        }
        if (hasDevanagari && hasLatin) {
            return SpokenLanguage.HINGLISH
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
