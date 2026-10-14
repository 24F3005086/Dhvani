package com.sameer.dhvani.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class AudioPlaybackManager {
    companion object {
        private const val TAG = "AudioPlaybackManager"
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = 22050
    private val audioQueue = LinkedBlockingQueue<FloatArray>()
    @Volatile private var isPlaying = false
    @Volatile private var isInterrupted = false
    private var playbackThread: Thread? = null

    init {
        startPlaybackWorker()
    }

    private fun ensureAudioTrack(sampleRate: Int) {
        if (audioTrack == null || currentSampleRate != sampleRate) {
            audioTrack?.release()
            currentSampleRate = sampleRate

            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val encoding = AudioFormat.ENCODING_PCM_FLOAT
            val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBufSize, 4096 * 4))
                .build()

            audioTrack?.play()
        }
    }

    private fun startPlaybackWorker() {
        isPlaying = true
        playbackThread = thread(name = "DhvaniAudioPlaybackThread") {
            while (isPlaying) {
                try {
                    val samples = audioQueue.take()
                    if (isInterrupted) {
                        continue
                    }

                    audioTrack?.let { track ->
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            track.play()
                        }
                        var offset = 0
                        while (offset < samples.size && !isInterrupted) {
                            val chunkSize = minOf(1024, samples.size - offset)
                            track.write(samples, offset, chunkSize, AudioTrack.WRITE_BLOCKING)
                            offset += chunkSize
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack playback error", e)
                }
            }
        }
    }

    fun playAudio(samples: FloatArray, sampleRate: Int) {
        isInterrupted = false
        ensureAudioTrack(sampleRate)
        audioQueue.offer(samples)
    }

    /**
     * Instantly stops playback and drops remaining audio queue when barge-in occurs.
     */
    fun bargeIn() {
        isInterrupted = true
        audioQueue.clear()
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack during barge-in", e)
        }
    }

    fun release() {
        isPlaying = false
        bargeIn()
        playbackThread?.interrupt()
        playbackThread = null
        audioTrack?.release()
        audioTrack = null
    }
}
