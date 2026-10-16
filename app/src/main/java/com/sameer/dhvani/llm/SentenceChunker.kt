package com.sameer.dhvani.llm

class SentenceChunker(
    private val onSentenceReady: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private val delimiters = setOf('.', '?', '!', '\n', '।')
    private var isFirstChunk = true

    fun addToken(token: String) {
        buffer.append(token)
        val text = buffer.toString()

        var cutIndex = -1

        // On first chunk, prioritize ultra-fast delivery at the first comma (e.g. "कल बैंगलोर में,")
        // if we already have 3 or more words
        if (isFirstChunk) {
            val commaIdx = text.indexOf(',')
            if (commaIdx != -1) {
                val wordsBeforeComma = text.substring(0, commaIdx).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (wordsBeforeComma.size >= 3) {
                    cutIndex = commaIdx
                    isFirstChunk = false
                }
            }
        }

        if (cutIndex == -1) {
            for (i in text.indices) {
                val char = text[i]
                if (delimiters.contains(char)) {
                    // Check if it's an abbreviation like "5 PM" or "e.g."
                    if (char == '.' && i > 0 && i + 1 < text.length && text[i + 1].isDigit()) {
                        continue
                    }
                    cutIndex = i
                    isFirstChunk = false
                    break
                }
            }
        }

        // If no punctuation yet, but buffer has grown past 10 words, break at comma if available
        if (cutIndex == -1 && text.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 10) {
            val commaIndex = text.indexOf(',')
            if (commaIndex != -1 && commaIndex > 6) {
                cutIndex = commaIndex
                isFirstChunk = false
            }
        }

        if (cutIndex != -1) {
            val sentence = text.substring(0, cutIndex + 1).trim()
            val remaining = text.substring(cutIndex + 1)
            buffer.clear()
            buffer.append(remaining)

            if (sentence.isNotEmpty()) {
                onSentenceReady(sentence)
            }
        }
    }

    fun finish() {
        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            onSentenceReady(remaining)
        }
        buffer.clear()
        isFirstChunk = true
    }

    fun reset() {
        buffer.clear()
        isFirstChunk = true
    }
}
