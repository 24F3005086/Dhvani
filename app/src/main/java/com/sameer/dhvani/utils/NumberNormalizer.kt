package com.sameer.dhvani.utils

object NumberNormalizer {

    private val HINDI_NUMBERS = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
        "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
        "इक्कीस", "बाइस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
        "इकत्तीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
        "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
        "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
        "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सरसठ", "अड़सठ", "उनहत्तर", "सत्तर",
        "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी", "अस्सी",
        "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
        "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे", "सौ"
    )

    private val ENGLISH_UNITS = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )

    private val ENGLISH_TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    fun hindiNumberToWords(num: Int): String {
        return when {
            num in 0..100 -> HINDI_NUMBERS[num]
            num in 101..999 -> {
                val hundreds = num / 100
                val remainder = num % 100
                val hStr = "${HINDI_NUMBERS[hundreds]} सौ"
                if (remainder > 0) "$hStr ${hindiNumberToWords(remainder)}" else hStr
            }
            num in 1000..99999 -> {
                val thousands = num / 1000
                val remainder = num % 1000
                val tStr = "${hindiNumberToWords(thousands)} हज़ार"
                if (remainder > 0) "$tStr ${hindiNumberToWords(remainder)}" else tStr
            }
            num in 100000..9999999 -> {
                val lakhs = num / 100000
                val remainder = num % 100000
                val lStr = "${hindiNumberToWords(lakhs)} लाख"
                if (remainder > 0) "$lStr ${hindiNumberToWords(remainder)}" else lStr
            }
            else -> num.toString()
        }
    }

    fun englishNumberToWords(num: Int): String {
        return when {
            num in 0..19 -> ENGLISH_UNITS[num]
            num in 20..99 -> {
                val tens = num / 10
                val units = num % 10
                if (units > 0) "${ENGLISH_TENS[tens]} ${ENGLISH_UNITS[units]}" else ENGLISH_TENS[tens]
            }
            num in 100..999 -> {
                val hundreds = num / 100
                val remainder = num % 100
                val hStr = "${ENGLISH_UNITS[hundreds]} hundred"
                if (remainder > 0) "$hStr ${englishNumberToWords(remainder)}" else hStr
            }
            num in 1000..999999 -> {
                val thousands = num / 1000
                val remainder = num % 1000
                val tStr = "${englishNumberToWords(thousands)} thousand"
                if (remainder > 0) "$tStr ${englishNumberToWords(remainder)}" else tStr
            }
            else -> num.toString()
        }
    }

    /**
     * Converts Devanagari numerals (०-९) to ASCII digits (0-9).
     */
    fun devanagariDigitsToAscii(text: String): String {
        val devToAscii = mapOf(
            '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
            '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
        )
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(devToAscii[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Comprehensive Hindi Number & Time Normalizer for TTS engines (Supertonic, Piper).
     * Converts numbers like 5 and 7 to पाँच and सात, and times like "5 PM" to "शाम पाँच बजे".
     */
    fun normalizeForHindi(rawText: String): String {
        var text = devanagariDigitsToAscii(rawText)

        // 1. Time with AM/PM (e.g. "5:00 PM" -> "शाम पाँच बजे", "5 PM" -> "शाम पाँच बजे")
        text = text.replace(Regex("(?i)(\\d{1,2}):00\\s*(pm|p\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 5
            "शाम ${hindiNumberToWords(hour)} बजे"
        }
        text = text.replace(Regex("(?i)(\\d{1,2}):(\\d{2})\\s*(pm|p\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 5
            val min = match.groupValues[2].toIntOrNull() ?: 0
            if (min == 30) "शाम साढ़े ${hindiNumberToWords(hour)} बजे"
            else "शाम ${hindiNumberToWords(hour)} बजकर ${hindiNumberToWords(min)} मिनट"
        }
        text = text.replace(Regex("(?i)(\\d{1,2})\\s*(pm|p\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 5
            "शाम ${hindiNumberToWords(hour)} बजे"
        }

        text = text.replace(Regex("(?i)(\\d{1,2}):00\\s*(am|a\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 7
            "सुबह ${hindiNumberToWords(hour)} बजे"
        }
        text = text.replace(Regex("(?i)(\\d{1,2}):(\\d{2})\\s*(am|a\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 7
            val min = match.groupValues[2].toIntOrNull() ?: 0
            if (min == 30) "सुबह साढ़े ${hindiNumberToWords(hour)} बजे"
            else "सुबह ${hindiNumberToWords(hour)} बजकर ${hindiNumberToWords(min)} मिनट"
        }
        text = text.replace(Regex("(?i)(\\d{1,2})\\s*(am|a\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 7
            "सुबह ${hindiNumberToWords(hour)} बजे"
        }

        // 2. Hindi time expressions (e.g. "7 baje", "7 बजे", "5 baje", "5 बजे")
        text = text.replace(Regex("(\\d{1,2})\\s*(baje|बजे)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 0
            "${hindiNumberToWords(hour)} बजे"
        }

        // 3. Digital clock time (e.g. "7:00" -> "सात बजे", "7:30" -> "साढ़े सात बजे")
        text = text.replace(Regex("(\\d{1,2}):00")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 0
            "${hindiNumberToWords(hour)} बजे"
        }
        text = text.replace(Regex("(\\d{1,2}):(\\d{2})")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 0
            val min = match.groupValues[2].toIntOrNull() ?: 0
            if (min == 30) "साढ़े ${hindiNumberToWords(hour)} बजे"
            else "${hindiNumberToWords(hour)} बजकर ${hindiNumberToWords(min)} मिनट"
        }

        // 4. Any standalone numbers (e.g. "5" -> "पाँच", "7" -> "सात", "28" -> "अट्ठाईस")
        text = text.replace(Regex("\\b(\\d+)\\b")) { match ->
            val num = match.groupValues[1].toIntOrNull()
            if (num != null) hindiNumberToWords(num) else match.value
        }

        return text
    }

    /**
     * English number normalizer for TTS.
     */
    fun normalizeForEnglish(rawText: String): String {
        var text = rawText

        // Time with AM/PM (e.g. "5 PM" -> "five PM", "7 AM" -> "seven AM")
        text = text.replace(Regex("(?i)(\\d{1,2})\\s*(pm|p\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 5
            "${englishNumberToWords(hour)} PM"
        }
        text = text.replace(Regex("(?i)(\\d{1,2})\\s*(am|a\\.m\\.)")) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 7
            "${englishNumberToWords(hour)} AM"
        }

        return text
    }
}
