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
        "ka", "ki", "ke", "ko", "se", "mein", "main", "mai", "par", "pe",
        // Verbs and auxiliaries
        "hai", "hain", "tha", "thi", "thay", "hoga", "hogi", "hoge", "karo", "karna", "kare", "kariye",
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
        "zaroor", "zarur", "pehle", "baad", "saath", "andar", "bahar", "chai", "peeni", "peena", "khana",
        "rupaye", "rupee", "rupees", "kharch", "bache", "bachte", "bachenge", "ghatane"
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
        "chennai", "distance", "kilometer", "kilometre", "km", "delhi", "mumbai", "hyderabad",
        "campus", "canteen", "nearest",
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
    /**
     * Comprehensive English loanwords transcribed into Devanagari by Whisper.
     * When any of these appear in a Devanagari transcript, the user was speaking Hinglish.
     */
    private val DEVANAGARI_HINGLISH_LOANWORDS = setOf(
        // Places & Campus
        "कैंपस", "कैम्पस", "केंपस", "कॉलेज", "यूनिवर्सिटी", "स्कूल", "डिपार्टमेंट", "डिपार्टमेन्ट",
        "लैब", "लाइब्रेरी", "क्लास", "रूम", "हॉस्टल", "गेट", "कैंटीन", "केन्टीन", "कैन्टीन", "मेस",
        "ऑफिस", "कंपनी", "हॉस्पिटल", "हस्पताल", "क्लिनिक", "होटल", "रेस्टोरेंट", "रेस्तरां",
        "कैफे", "शॉप", "स्टोर", "मार्केट", "मॉल", "सुपरमार्केट", "पार्क",

        // Travel & Navigation
        "सिटी", "सिटि", "सेंटर", "सेन्टर", "एयरपोर्ट", "एअरपोर्ट", "हवाईअड्डा",
        "चेन्नई", "दिल्ली", "मुंबई", "हैदराबाद",
        "फ्लाइट", "प्लेन", "टिकट", "ट्रेन", "मेट्रो", "बस", "कैब", "टैक्सी", "ऑटो",
        "स्टेशन", "स्टॉप", "रूट", "ट्रैफिक", "हाईवे", "रोड", "स्ट्रीट", "लोकेशन", "मैप", "नेविगेशन",
        "डिस्टेंस", "डिस्टन्स", "डिस्टेन्स", "किलोमीटर", "किमी", "मीटर", "माइल", "स्पीड",

        // Scheduling & Routine
        "नेक्स्ट",
        "शेड्यूल", "सेड्यूल", "शिड्यूल", "रूटीन", "टाइम", "टाइमिंग", "आवर", "ऑवर", "मिनट", "सेकंड",
        "अलार्म", "अलारम", "टाइमर", "क्लॉक", "रिमाइंडर", "रिमैन्डर", "कैलेंडर",
        "मीटिंग", "अपॉइंटमेंट", "प्रेजेंटेशन", "प्रेज़ेंटेशन", "प्रजेंटेशन", "स्लाइड्स", "रिपोर्ट",
        "प्रोजेक्ट", "असाइनमेंट", "टास्क", "वर्क", "जॉब", "इंटरव्यू", "एग्जाम", "टेस्ट",

        // Weather & Science
        "वेदर", "फोरकास्ट", "क्लाइमेट", "टेंपरेचर", "डिग्री", "सेल्सियस",

        // Tech & Communication
        "फोन", "मोबाइल", "स्मार्टफोन", "लैपटॉप", "कंप्यूटर", "ऐप", "एप्लिकेशन", "वेबसाइट", "वेबसाईट", "साइट", "पोर्टल",
        "कॉल", "मैसेज", "मैसेजिंग", "एसएमएस", "ईमेल", "नोटिफिकेशन", "पासवर्ड", "ओटीपी", "कोड",
        "इंटरनेट", "वाईफाई", "नेटवर्क", "सिम", "चार्ज", "चार्जर", "बैटरी", "स्क्रीन",

        // Common Adjectives & Modifiers
        "निरेस्ट", "नियरेस्ट", "नियरस्ट", "क्लोजेस्ट", "बेस्ट", "टॉप", "फेमस", "पॉपुलर",
        "चीप", "एक्सपेंसिव", "फास्ट", "क्विक", "स्लो", "इजी", "हार्ड", "सिंपल", "डिफिकल्ट",
        "लेटेस्ट", "करेंट", "न्यू", "इंपॉर्टेंट", "अर्जेंट",

        // Actions & Verbs
        "सेट", "कैलकुलेट", "मल्टीप्लाई", "डिवाइड", "सॉल्व", "चेक", "कन्फर्म", "वेरीफाई",
        "कैंसिल", "बुक", "बुकिंग", "स्टार्ट", "स्टॉप", "ओपन", "क्लोज", "टर्न", "डाउनलोड", "अपलोड",

        // Food & Daily
        "कॉफी", "टी", "लंच", "डिनर", "ब्रेकफास्ट", "स्नैक", "मील", "फूड", "ड्रिंक", "वाटर", "बॉटल", "ऑर्डर", "बिल",
        "हेल्प", "सपोर्ट", "प्रॉब्लम", "इशू", "सॉल्यूशन", "प्लान", "आईडिया", "न्यूज", "अपडेट"
    )

    private fun normalizeDevanagariWord(w: String): String {
        return w.replace('श', 'स')
            .replace('ष', 'स')
            .replace("य", "")
            .replace('ं', 'म')
            .replace("म्", "म")
            .replace("न्", "म")
            .replace("्", "")
    }

    private val NORMALIZED_LOANWORDS = DEVANAGARI_HINGLISH_LOANWORDS.map { normalizeDevanagariWord(it) }.toSet()

    /**
     * Checks if a Devanagari string contains English loanwords (identifying Hinglish).
     */
    fun hasDevanagariLoanword(text: String): Boolean {
        val tokens = text.lowercase().split(Regex("[\\s,?.!;:\"'()|।]+")).filter { it.isNotBlank() }
        for (token in tokens) {
            if (DEVANAGARI_HINGLISH_LOANWORDS.contains(token)) return true
            val norm = normalizeDevanagariWord(token)
            if (NORMALIZED_LOANWORDS.contains(norm)) return true
            for (loanword in DEVANAGARI_HINGLISH_LOANWORDS) {
                if (token.length >= 4 && loanword.length >= 4 && token.startsWith(loanword)) {
                    return true
                }
            }
        }
        return false
    }

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
        val isDevanagariHinglish = hasDevanagariLoanword(trimmed)

        Log.d(TAG, "LID Analysis: words=${words.size}, enHits=$englishHits, hiHits=$hindiHits, isDevHinglish=$isDevanagariHinglish, whisperLang='$whisperLanguage', text='$trimmed'")

        // 1. If mixed script (Devanagari + Latin) or contains Devanagari loanwords -> HINGLISH
        if (hasLatin && hasDevanagari) {
            Log.d(TAG, "Mixed script detected -> HINGLISH")
            return SpokenLanguage.HINGLISH
        }
        if (isDevanagariHinglish) {
            Log.d(TAG, "Devanagari English loanwords detected -> HINGLISH")
            return SpokenLanguage.HINGLISH
        }

        // 2. Whisper detected English (or acoustic model thinks English)
        if (wLang == "en" || wLang == "english") {
            // Did user speak Hinglish in Roman script?
            if (hindiHits >= 1) {
                Log.d(TAG, "Whisper English with distinct Hindi markers ($hindiHits hiHits) -> HINGLISH")
                return SpokenLanguage.HINGLISH
            }
            if (hasDevanagari) {
                return SpokenLanguage.HINDI
            }
            return SpokenLanguage.ENGLISH
        }

        // 3. Whisper detected Hindi or Urdu
        if (wLang == "hi" || wLang == "hindi" || wLang == "ur" || wLang == "urdu") {
            // If Latin script -> could be Hinglish or Indian-accented English
            if (hasLatin) {
                if (hindiHits >= 1) {
                    Log.d(TAG, "Whisper Hindi with Latin script tokens and Hindi markers -> HINGLISH")
                    return SpokenLanguage.HINGLISH
                }
                Log.d(TAG, "Whisper Hindi with pure English text -> ENGLISH")
                return SpokenLanguage.ENGLISH
            }
            // Otherwise, pure Hindi! ("कल बैंगलोर का मौसम कैसा रहेगा?", "भारत के प्रधानमंत्री कौन हैं?")
            Log.d(TAG, "Whisper Hindi without loanwords -> HINDI")
            return SpokenLanguage.HINDI
        }

        // 4. Fallback when acoustic language is unknown
        if (hasDevanagari) {
            return if (isDevanagariHinglish) SpokenLanguage.HINGLISH else SpokenLanguage.HINDI
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

        if (hasLatin && hasDevanagari) {
            return SpokenLanguage.HINGLISH
        }
        if (hasDevanagari) {
            return if (hasDevanagariLoanword(trimmed)) SpokenLanguage.HINGLISH else SpokenLanguage.HINDI
        }

        val words = trimmed.lowercase().split(Regex("[\\s,?.!;:\"'()|।]+")).filter { it.isNotBlank() }
        val hindiHits = words.count { UNAMBIGUOUS_HINDI_LEXICON.contains(it) }

        return if (hindiHits >= 1) SpokenLanguage.HINGLISH else SpokenLanguage.ENGLISH
    }

    /**
     * Determines whether to use Hindi voice based on text script and detected language.
     */
    fun shouldUseHindiVoice(text: String, detectedLang: SpokenLanguage): Boolean {
        if (text.any { it.code in 0x0900..0x097F }) return true
        return detectedLang == SpokenLanguage.HINGLISH || detectedLang == SpokenLanguage.HINDI
    }
}
