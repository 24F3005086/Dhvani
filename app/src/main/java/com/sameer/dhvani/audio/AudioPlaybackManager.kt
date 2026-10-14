package com.sameer.dhvani.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AudioPlaybackManager(
    var onPlaybackStarted: (() -> Unit)? = null,
    var onPlaybackFinished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlaybackManager"
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = 22050
    private val audioQueue = LinkedBlockingQueue<FloatArray>()

    @Volatile
    private var isWorkerRunning = false

    @Volatile
    var isActivelyPlaying: Boolean = false
        private set

    @Volatile
    private var isExpectingMoreAudio: Boolean = false

    @Volatile
    private var isInterrupted = false

    private var playbackThread: Thread? = null

    init {
        startPlaybackWorker()
    }

    private fun ensureAudioTrack(sampleRate: Int) {
        if (audioTrack == null || currentSampleRate != sampleRate) {
            try {
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing previous AudioTrack", e)
            }
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
        isWorkerRunning = true
        playbackThread = thread(name = "DhvaniAudioPlaybackThread") {
            while (isWorkerRunning) {
                try {
                    // Poll with timeout so we can detect when queue is empty and playback is complete
                    val samples = audioQueue.poll(150, TimeUnit.MILLISECONDS)
                    if (isInterrupted) {
                        continue
                    }

                    if (samples != null) {
                        if (!isActivelyPlaying) {
                            isActivelyPlaying = true
                            onPlaybackStarted?.invoke()
                        }

                        ensureAudioTrack(currentSampleRate)
                        audioTrack?.let { track ->
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                track.play()
                            }
                            var offset = 0
                            while (offset < samples.size && !isInterrupted && isWorkerRunning) {
                                val chunkSize = minOf(1024, samples.size - offset)
                                val written = track.write(samples, offset, chunkSize, AudioTrack.WRITE_BLOCKING)
                                if (written < 0) {
                                    Log.w(TAG, "AudioTrack write returned error: $written")
                                    break
                                }
                                offset += chunkSize
                            }
                        }
                    } else {
                        // Queue is empty: check if we are finished with current utterance
                        if (isActivelyPlaying && !isExpectingMoreAudio && audioQueue.isEmpty()) {
                            // Wait briefly for the audio buffer in hardware to drain out
                            try {
                                Thread.sleep(200)
                            } catch (_: InterruptedException) {
                                break
                            }

                            if (audioQueue.isEmpty() && !isExpectingMoreAudio) {
                                Log.d(TAG, "Playback completed for current utterance")
                                isActivelyPlaying = false
                                onPlaybackFinished?.invoke()
                            }
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
        currentSampleRate = sampleRate
        audioQueue.offer(samples)
    }

    fun setExpectingMoreAudio(expecting: Boolean) {
        isExpectingMoreAudio = expecting
        Log.d(TAG, "setExpectingMoreAudio: $expecting, queueSize: ${audioQueue.size}, isPlaying: $isActivelyPlaying")
        // If not expecting more and queue is already empty, trigger completion
        if (!expecting && audioQueue.isEmpty() && isActivelyPlaying) {
            // Worker loop will detect this on the next iteration
        }
    }

    /**
     * Instantly stops playback, clears the audio queue, and flushes hardware buffers.
     */
    fun bargeIn() {
        Log.d(TAG, "Barge-in triggered: stopping playback immediately")
        isInterrupted = true
        isExpectingMoreAudio = false
        isActivelyPlaying = false
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
        isWorkerRunning = false
        bargeIn()
        playbackThread?.interrupt()
        playbackThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
