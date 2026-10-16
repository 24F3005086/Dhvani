package com.sameer.dhvani.llm

/**
 * Natural Sentence Chunker for Dhvani Speech Pipeline.
 *
 * Designed for fluent, continuous speech:
 * - Only breaks at TRUE sentence boundaries (।, ., ?, !, \n).
 * - NEVER cuts mid-sentence or at commas. Commas remain inside the sentence so
 *   the neural TTS model (Supertonic) produces natural, fluid prosodic pauses.
 * - Entire conversational answers (typically 10-15 words) are synthesized as a
 *   single coherent, continuous utterance with perfect cadence and zero stutter.
 */
class SentenceChunker(
    private val onSentenceReady: (sentence: String, isFirstChunk: Boolean) -> Unit
) {
    private val buffer = StringBuilder()
    private val sentenceDelimiters = setOf('।', '.', '?', '!', '\n')
    private var isFirstChunk = true

    fun addToken(token: String) {
        buffer.append(token)
        val text = buffer.toString()

        var cutIndex = -1
        for (i in text.indices) {
            val char = text[i]
            if (sentenceDelimiters.contains(char)) {
                // Ignore decimal numbers (e.g. 28.5)
                if (char == '.' && i > 0 && i + 1 < text.length && text[i + 1].isDigit()) {
                    continue
                }
                cutIndex = i
                break
            }
        }

        // Only for exceptionally long sentences (>25 words) with no sentence delimiter,
        // break at a major clause mark to prevent excessive latency.
        if (cutIndex == -1) {
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 25) {
                val clauseIdx = text.indexOfAny(charArrayOf(';', '—'))
                if (clauseIdx > 15) {
                    cutIndex = clauseIdx
                } else {
                    val lastSpace = text.lastIndexOf(' ')
                    if (lastSpace > 0) {
                        cutIndex = lastSpace
                    }
                }
            }
        }

        if (cutIndex != -1) {
            val chunk = text.substring(0, cutIndex + 1).trim()
            val remaining = text.substring(cutIndex + 1)
            buffer.clear()
            buffer.append(remaining)

            if (chunk.isNotEmpty()) {
                val wasFirst = isFirstChunk
                isFirstChunk = false
                onSentenceReady(chunk, wasFirst)
            }
        }
    }

    fun finish() {
        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            val wasFirst = isFirstChunk
            onSentenceReady(remaining, wasFirst)
        }
        buffer.clear()
        isFirstChunk = true
    }

    fun reset() {
        buffer.clear()
        isFirstChunk = true
    }
}
