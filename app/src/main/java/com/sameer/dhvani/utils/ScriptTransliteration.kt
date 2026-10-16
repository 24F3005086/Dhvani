package com.sameer.dhvani.utils

object ScriptTransliteration {

    /**
     * Common bilingual dictionary for natural, professional Hindi <-> Hinglish mapping.
     */
    private val DEVANAGARI_TO_ROMAN = mapOf(
        "नमस्ते" to "Namaste", "नमस्कार" to "Namaskar",
        "मौसम" to "mausam", "सुहावना" to "suhana", "तापमान" to "tapman", "डिग्री" to "degree",
        "बैंगलोर" to "Bangalore", "आईआईएससी" to "IISc", "कैंपस" to "campus", "कैंटीन" to "canteen",
        "नियरेस्ट" to "nearest", "अलार्म" to "alarm", "मीटिंग" to "meeting", "शेड्यूल" to "schedule",
        "रिमाइंडर" to "reminder", "रुपये" to "rupaye", "रुपए" to "rupaye", "खर्च" to "kharch",
        "कितने" to "kitne", "कितना" to "kitna", "कितनी" to "kitni", "बचे" to "bache",
        "बचते" to "bachte", "बचेंगे" to "bachenge", "घटाने" to "ghatane", "पर" to "par",
        "कल" to "kal", "आज" to "aaj", "सुबह" to "subah", "शाम" to "shaam", "रात" to "raat",
        "बजे" to "baje", "सात" to "saat", "पाँच" to "paanch", "सौ" to "sau", "हज़ार" to "hazaar",
        "प्रधानमंत्री" to "Pradhan Mantri", "भारत" to "Bharat", "कौन" to "kaun",
        "कहाँ" to "kahan", "कहा" to "kahan", "कैसे" to "kaise", "क्या" to "kya", "कब" to "kab",
        "हैं" to "hain", "है" to "hai", "था" to "tha", "थी" to "thi", "थे" to "the",
        "होगा" to "hoga", "होगी" to "hogi", "रहेगा" to "rahega", "रहेगी" to "rahegi",
        "कर" to "kar", "करो" to "karo", "करना" to "karna", "करने" to "karne",
        "दिया" to "diya", "गया" to "gaya", "गयी" to "gayi", "लिए" to "liye", "सेट" to "set",
        "मदद" to "madad", "चाय" to "chai", "पीनी" to "peeni", "पास" to "paas",
        "बिल्कुल" to "bilkul", "ठीक" to "theek", "अच्छा" to "accha", "धन्यवाद" to "dhanyavaad",
        "शुक्रिया" to "shukriya", "आपका" to "aapka", "आपकी" to "aapki", "आपके" to "aapke",
        "मेरा" to "mera", "मेरी" to "meri", "मेरे" to "mere", "मुझे" to "mujhe",
        "हमारा" to "hamara", "और" to "aur", "तो" to "toh", "अगर" to "agar",
        "नहीं" to "nahi", "हाँ" to "haan", "मेन" to "main", "मुख्य" to "mukhya",
        "परिसर" to "parisar", "पुस्तकालय" to "pustakalaya", "भोजन" to "bhojan", "कक्ष" to "kaksh",
        "वर्तमान" to "vartaman", "श्री" to "Shri", "नरेंद्र" to "Narendra", "मोदी" to "Modi",
        "आवेदन" to "aavedan", "वेबसाइट" to "website", "आधिकारिक" to "adhikarik", "प्रवेश" to "pravesh",
        "लगभग" to "lagbhag", "अट्ठाईस" to "atthaees", "समय" to "samay", "ट्रैफिक" to "traffic"
    )

    private val ROMAN_TO_DEVANAGARI = mutableMapOf<String, String>().apply {
        for ((dev, rom) in DEVANAGARI_TO_ROMAN) {
            put(rom.lowercase(), dev)
        }
        // Additional phonetic variants
        put("rupees", "रुपये")
        put("bengaluru", "बैंगलोर")
        put("bangalore", "बैंगलोर")
        put("iisc", "आईआईएससी")
        put("campus", "कैंपस")
        put("canteen", "कैंटीन")
        put("nearest", "नियरेस्ट")
        put("alarm", "अलार्म")
        put("weather", "मौसम")
        put("forecast", "पूर्वानुमान")
        put("scheduled", "शेड्यूल")
        put("schedule", "शेड्यूल")
        put("reminder", "रिमाइंडर")
        put("namaste", "नमस्ते")
        put("namaskar", "नमस्कार")
        put("thik", "ठीक")
        put("theek", "ठीक")
        put("achha", "अच्छा")
        put("accha", "अच्छा")
        put("batao", "बताओ")
        put("bataiye", "बताइए")
        put("bolo", "बोलो")
        put("suhana", "सुहावना")
        put("suhavana", "सुहावना")
        put("degree", "डिग्री")
        put("temperature", "तापमान")
        put("paas", "पास")
        put("chai", "चाय")
        put("peeni", "पीनी")
        put("main", "मेन")
    }

    private val DEV_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z",
        '\u095C' to "d", '\u095D' to "dh", '\u095E' to "f"
    )

    private val DEV_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )

    private val DEV_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )

    private val DEV_DIGITS = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val ASCII_DIGITS_TO_DEV = mapOf(
        '0' to '०', '1' to '१', '2' to '२', '3' to '३', '4' to '४',
        '5' to '५', '6' to '६', '7' to '७', '8' to '८', '9' to '९'
    )

    fun hasDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }

    fun hasLatin(text: String): Boolean = text.any { (it in 'a'..'z') || (it in 'A'..'Z') }

    /**
     * Converts Devanagari text to natural, readable Roman Hinglish.
     * Uses high-quality dictionary words first, then fallback phonetic rules.
     */
    fun devanagariToRoman(text: String): String {
        if (!hasDevanagari(text)) return text

        val tokens = text.split(" ")
        val resultTokens = tokens.map { token ->
            val leadPunct = token.takeWhile { !isDevanagariChar(it) && !it.isDigit() }
            val trailPunct = token.takeLastWhile { !isDevanagariChar(it) && !it.isDigit() }
            val core = token.substring(leadPunct.length, token.length - trailPunct.length)

            val cleanPunct = trailPunct.replace("।", ".")

            if (core.isEmpty()) {
                leadPunct + cleanPunct
            } else {
                val matched = DEVANAGARI_TO_ROMAN[core]
                if (matched != null) {
                    leadPunct + matched + cleanPunct
                } else {
                    leadPunct + transliterateDevWord(core) + cleanPunct
                }
            }
        }
        return resultTokens.joinToString(" ")
    }

    /**
     * Converts Roman Hinglish text to Devanagari script.
     */
    fun romanToDevanagari(text: String): String {
        val tokens = text.split(" ")
        val resultTokens = tokens.map { token ->
            val cleanWord = token.lowercase().trim { !it.isLetterOrDigit() }
            val lead = token.takeWhile { !it.isLetterOrDigit() }
            val trail = token.takeLastWhile { !it.isLetterOrDigit() }

            val dev = ROMAN_TO_DEVANAGARI[cleanWord]
            if (dev != null) {
                "$lead$dev$trail"
            } else if (cleanWord.all { it.isDigit() }) {
                val devNum = cleanWord.map { ASCII_DIGITS_TO_DEV[it] ?: it }.joinToString("")
                "$lead$devNum$trail"
            } else {
                token
            }
        }
        return resultTokens.joinToString(" ").replace(".", "।")
    }

    private fun isDevanagariChar(c: Char): Boolean = c.code in 0x0900..0x097F

    private fun transliterateDevWord(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]

            // Digits
            if (DEV_DIGITS.containsKey(c)) {
                sb.append(DEV_DIGITS[c])
                i++
                continue
            }

            // Independent Vowels
            if (DEV_VOWELS.containsKey(c)) {
                sb.append(DEV_VOWELS[c])
                i++
                continue
            }

            // Consonants
            if (DEV_CONSONANTS.containsKey(c)) {
                val cons = DEV_CONSONANTS[c]!!
                sb.append(cons)

                // Check next character: virama (halant), matra, or implicit 'a'
                if (i + 1 < word.length) {
                    val next = word[i + 1]
                    if (next == '्') {
                        // Halant: no vowel attached
                        i += 2
                        continue
                    } else if (DEV_MATRAS.containsKey(next)) {
                        sb.append(DEV_MATRAS[next])
                        i += 2
                        continue
                    } else if (isDevanagariChar(next) && DEV_CONSONANTS.containsKey(next)) {
                        // Implicit short 'a' before another consonant
                        sb.append("a")
                        i++
                        continue
                    }
                }
                i++
                continue
            }

            // Standalone Matras (if any)
            if (DEV_MATRAS.containsKey(c)) {
                sb.append(DEV_MATRAS[c])
                i++
                continue
            }

            if (c == '।') {
                sb.append(".")
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
