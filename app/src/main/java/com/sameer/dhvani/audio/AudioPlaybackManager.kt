package com.sameer.dhvani.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class AudioPlaybackChunk(
    val samples: FloatArray,
    val sampleRate: Int
)

class AudioPlaybackManager(
    var onPlaybackStarted: (() -> Unit)? = null,
    var onPlaybackFinished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlaybackManager"
    }

    private var audioTrack: AudioTrack? = null
    private var configuredTrackSampleRate: Int = -1
    private val audioQueue = LinkedBlockingQueue<AudioPlaybackChunk>()

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

    private fun ensureAudioTrack(targetSampleRate: Int): AudioTrack? {
        if (audioTrack == null || configuredTrackSampleRate != targetSampleRate) {
            try {
                audioTrack?.apply {
                    if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                        stop()
                    }
                    release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing previous AudioTrack", e)
            }
            audioTrack = null

            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val encoding = AudioFormat.ENCODING_PCM_FLOAT
            val minBufSize = AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, encoding)
            // Generous hardware buffer (minimum 1 second of float audio) to prevent underruns
            val bufferSizeInBytes = maxOf(minBufSize * 2, targetSampleRate * 4)

            try {
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
                            .setSampleRate(targetSampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build()

                configuredTrackSampleRate = targetSampleRate
                audioTrack?.play()
                Log.d(TAG, "Configured AudioTrack for native sampleRate: $targetSampleRate Hz (bufSize: $bufferSizeInBytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioTrack for sampleRate: $targetSampleRate", e)
                configuredTrackSampleRate = -1
                return null
            }
        }
        return audioTrack
    }

    private fun startPlaybackWorker() {
        isWorkerRunning = true
        playbackThread = thread(name = "DhvaniAudioPlaybackThread") {
            while (isWorkerRunning) {
                try {
                    val chunk = audioQueue.poll(150, TimeUnit.MILLISECONDS)
                    if (isInterrupted) {
                        continue
                    }

                    if (chunk != null) {
                        if (!isActivelyPlaying) {
                            isActivelyPlaying = true
                            onPlaybackStarted?.invoke()
                        }

                        val track = ensureAudioTrack(chunk.sampleRate)
                        track?.let { activeTrack ->
                            if (activeTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                activeTrack.play()
                            }
                            val samples = chunk.samples
                            var offset = 0
                            while (offset < samples.size && !isInterrupted && isWorkerRunning) {
                                val toWrite = minOf(2048, samples.size - offset)
                                val written = activeTrack.write(samples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                                if (written < 0) {
                                    Log.w(TAG, "AudioTrack write returned error: $written")
                                    break
                                }
                                offset += written
                            }
                        }
                    } else {
                        // Queue empty: check if utterance is finished
                        if (isActivelyPlaying && !isExpectingMoreAudio && audioQueue.isEmpty()) {
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
                    Log.e(TAG, "AudioTrack playback loop error", e)
                }
            }
        }
    }

    fun playAudio(samples: FloatArray, sampleRate: Int) {
        isInterrupted = false
        audioQueue.offer(AudioPlaybackChunk(samples, sampleRate))
    }

    fun setExpectingMoreAudio(expecting: Boolean) {
        isExpectingMoreAudio = expecting
        Log.d(TAG, "setExpectingMoreAudio: $expecting, queueSize: ${audioQueue.size}, isPlaying: $isActivelyPlaying")
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
        configuredTrackSampleRate = -1
    }
}
