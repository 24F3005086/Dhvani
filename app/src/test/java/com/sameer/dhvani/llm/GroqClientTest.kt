package com.sameer.dhvani.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GroqClientTest {

    @Test
    fun testStripThinkingTags() {
        val inputWithThink = "<think>\nThinking about the user query...\nLet's keep it short.\n</think>Hello! I am Dhvani."
        val cleaned = GroqClient.stripThinkingTags(inputWithThink)
        assertEquals("Hello! I am Dhvani.", cleaned)
    }

    @Test
    fun testStripUnclosedThinkingTags() {
        val inputUnclosed = "<think>Thinking still in progress..."
        val cleaned = GroqClient.stripThinkingTags(inputUnclosed)
        assertEquals("", cleaned)
    }

    @Test
    fun testStripOrphanTags() {
        val input = "Hello <think> world </think> test"
        val cleaned = GroqClient.stripThinkingTags(input)
        assertEquals("Hello  test", cleaned)
    }

    @Test
    fun testPcmFloatToWavHeader() {
        // Create 100 samples of a sine-like or dummy wave
        val samples = FloatArray(160) { i -> (i / 160f) * 2f - 1f }
        val wavBytes = GroqClient.pcmFloatToWav(samples, sampleRate = 16000)

        // WAV header is 44 bytes + 160 samples * 2 bytes = 364 bytes
        assertEquals(44 + 160 * 2, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 0..3: "RIFF"
        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff))

        // 4..7: ChunkSize = 36 + 320 = 356
        val chunkSize = buffer.getInt()
        assertEquals(36 + 320, chunkSize)

        // 8..11: "WAVE"
        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave))

        // 12..15: "fmt "
        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt))

        // 16..19: Subchunk1Size = 16
        assertEquals(16, buffer.getInt())

        // 20..21: AudioFormat = 1 (PCM)
        assertEquals(1.toShort(), buffer.getShort())

        // 22..23: NumChannels = 1 (Mono)
        assertEquals(1.toShort(), buffer.getShort())

        // 24..27: SampleRate = 16000
        assertEquals(16000, buffer.getInt())

        // 28..31: ByteRate = 32000
        assertEquals(32000, buffer.getInt())

        // 32..33: BlockAlign = 2
        assertEquals(2.toShort(), buffer.getShort())

        // 34..35: BitsPerSample = 16
        assertEquals(16.toShort(), buffer.getShort())

        // 36..39: "data"
        val dataTag = ByteArray(4)
        buffer.get(dataTag)
        assertEquals("data", String(dataTag))

        // 40..43: Data size = 320
        assertEquals(320, buffer.getInt())
    }
}
