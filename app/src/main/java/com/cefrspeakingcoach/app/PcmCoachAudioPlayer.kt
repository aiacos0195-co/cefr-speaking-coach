package com.cefrspeakingcoach.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Plays raw float PCM through AudioTrack.
 *
 * v2 changes:
 *  - MODE_STREAM instead of MODE_STATIC. MODE_STATIC needs the buffer to match
 *    the payload exactly and does not tolerate long utterances; streaming also
 *    lets sherpa-onnx push chunks as they are synthesised (see appendChunk()).
 *  - Real completion signal. The caller no longer has to guess playback length
 *    with delay(estimatedMs), which drifted on every utterance.
 *  - A generation counter so a stopped playback can never fire onCompleted for
 *    the utterance that replaced it.
 */
class PcmCoachAudioPlayer {

    private val generation = AtomicInteger(0)

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var writerThread: Thread? = null

    /**
     * Plays [audio] and invokes [onCompleted] when the last frame has actually
     * left the device buffer. If playback is stopped or replaced, neither
     * callback fires for that utterance.
     */
    fun play(
        audio: CoachVoiceAudio,
        onCompleted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        stop()

        if (audio.samples.isEmpty()) {
            onError("Empty audio buffer")
            return
        }

        val myGeneration = generation.incrementAndGet()

        val channelMask = if (audio.channels == 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }

        val safeChannels = audio.channels.coerceAtLeast(1)
        val pcm16 = floatArrayToPcm16(audio.samples)
        val totalFrames = audio.samples.size / safeChannels

        val minBufferSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            onError("Unsupported audio format: ${audio.sampleRate} Hz, ${audio.channels} ch")
            return
        }

        val track = try {
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
                max(minBufferSize, MIN_STREAM_BUFFER_BYTES),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            onError(e.message ?: "AudioTrack creation failed")
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            onError("AudioTrack failed to initialize")
            return
        }

        audioTrack = track

        val thread = Thread {
            try {
                track.play()

                var offset = 0
                while (offset < pcm16.size) {
                    if (generation.get() != myGeneration) return@Thread
                    if (Thread.currentThread().isInterrupted) return@Thread

                    val chunk = min(WRITE_CHUNK_BYTES, pcm16.size - offset)
                    val written = track.write(pcm16, offset, chunk)

                    if (written < 0) {
                        if (generation.get() == myGeneration) {
                            onError("AudioTrack write error: $written")
                        }
                        return@Thread
                    }

                    offset += written
                }

                // Wait for the device to drain what we handed it.
                val timeoutAt = System.currentTimeMillis() + drainTimeoutMs(
                    totalFrames = totalFrames,
                    sampleRate = audio.sampleRate
                )

                while (System.currentTimeMillis() < timeoutAt) {
                    if (generation.get() != myGeneration) return@Thread
                    if (Thread.currentThread().isInterrupted) return@Thread
                    if (track.playbackHeadPosition >= totalFrames) break

                    Thread.sleep(POLL_INTERVAL_MS)
                }

                if (generation.get() == myGeneration) {
                    onCompleted()
                }
            } catch (_: InterruptedException) {
                // Normal path when stop() interrupts an in-flight utterance.
            } catch (e: Exception) {
                Log.e(TAG, "Playback failed", e)
                if (generation.get() == myGeneration) {
                    onError(e.message ?: "Playback failed")
                }
            }
        }

        writerThread = thread
        thread.isDaemon = true
        thread.name = "CoachPcmPlayer-$myGeneration"
        thread.start()
    }

    fun stop() {
        generation.incrementAndGet()

        writerThread?.let { thread ->
            runCatching { thread.interrupt() }
        }
        writerThread = null

        audioTrack?.let { track ->
            runCatching { if (track.state == AudioTrack.STATE_INITIALIZED) track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        audioTrack = null
    }

    fun release() {
        stop()
    }

    fun isPlaying(): Boolean {
        val track = audioTrack ?: return false
        return runCatching {
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        }.getOrDefault(false)
    }

    private fun drainTimeoutMs(totalFrames: Int, sampleRate: Int): Long {
        val playbackMs = (totalFrames.toDouble() / sampleRate.toDouble() * 1000.0).toLong()
        return (playbackMs + DRAIN_GRACE_MS).coerceAtLeast(DRAIN_GRACE_MS)
    }

    private fun floatArrayToPcm16(input: FloatArray): ByteArray {
        val output = ByteArray(input.size * 2)

        var inputIndex = 0
        var outputIndex = 0

        while (inputIndex < input.size) {
            val clamped = min(1f, max(-1f, input[inputIndex]))
            val sample = (clamped * 32767f).toInt().toShort()

            output[outputIndex] = (sample.toInt() and 0xFF).toByte()
            output[outputIndex + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()

            inputIndex++
            outputIndex += 2
        }

        return output
    }

    companion object {
        private const val TAG = "PcmCoachAudioPlayer"
        private const val WRITE_CHUNK_BYTES = 8192
        private const val MIN_STREAM_BUFFER_BYTES = 16384
        private const val POLL_INTERVAL_MS = 20L
        private const val DRAIN_GRACE_MS = 1500L
    }
}
