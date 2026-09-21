package com.cefrspeakingcoach.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object WavCoachAudioDecoder {

    fun decode(bytes: ByteArray): CoachVoiceAudio {
        require(bytes.size > 44) {
            "Invalid WAV file"
        }

        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)

        require(riff == "RIFF" && wave == "WAVE") {
            "Only RIFF/WAVE files are supported"
        }

        var offset = 12

        var audioFormat = -1
        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = readIntLE(bytes, offset + 4)
            val chunkDataOffset = offset + 8

            when (chunkId) {
                "fmt " -> {
                    audioFormat = readShortLE(bytes, chunkDataOffset).toInt()
                    channels = readShortLE(bytes, chunkDataOffset + 2).toInt()
                    sampleRate = readIntLE(bytes, chunkDataOffset + 4)
                    bitsPerSample = readShortLE(bytes, chunkDataOffset + 14).toInt()
                }

                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                    break
                }
            }

            offset = chunkDataOffset + chunkSize
            if (offset % 2 != 0) offset++
        }

        require(audioFormat == 1) {
            "Only PCM WAV files are supported"
        }

        require(bitsPerSample == 16) {
            "Only 16-bit PCM WAV files are supported"
        }

        require(channels == 1 || channels == 2) {
            "Only mono or stereo WAV files are supported"
        }

        require(dataOffset >= 0 && dataSize > 0) {
            "Missing WAV data chunk"
        }

        val safeDataSize = max(0, minOf(dataSize, bytes.size - dataOffset))
        val sampleCount = safeDataSize / 2
        val samples = FloatArray(sampleCount)

        var inputIndex = dataOffset
        var outputIndex = 0

        while (outputIndex < sampleCount && inputIndex + 1 < bytes.size) {
            val pcm = readShortLE(bytes, inputIndex)
            samples[outputIndex] = pcm / 32768f

            outputIndex++
            inputIndex += 2
        }

        return CoachVoiceAudio(
            samples = samples,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer
            .wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Short {
        return ByteBuffer
            .wrap(bytes, offset, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
    }
}
