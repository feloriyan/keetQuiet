package com.example.voicetranscriber.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioResampler {
    fun resample(input: ByteArray, inRate: Int, inChannels: Int, outRate: Int, outChannels: Int): ByteArray {
        if (inRate == outRate && inChannels == outChannels) return input

        val shortBuffer = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(samples)

        // 1. Downmix to Mono if needed
        val monoSamples = if (inChannels > 1) {
            ShortArray(samples.size / inChannels) { i ->
                var sum = 0
                for (c in 0 until inChannels) {
                    sum += samples[i * inChannels + c]
                }
                (sum / inChannels).toShort()
            }
        } else {
            samples
        }

        // 2. Resample Rate (Linear Interpolation) if needed
        if (inRate == outRate) {
            return toByteArray(monoSamples)
        }

        val ratio = inRate.toDouble() / outRate.toDouble()
        val outCount = (monoSamples.size / ratio).toInt()
        val outSamples = ShortArray(outCount)

        for (i in 0 until outCount) {
            val srcIdx = i * ratio
            val idx = srcIdx.toInt()
            val nextIdx = (idx + 1).coerceAtMost(monoSamples.lastIndex)
            val frac = srcIdx - idx

            val val1 = monoSamples[idx].toInt()
            val val2 = monoSamples[nextIdx].toInt()
            // Linear interpolation
            outSamples[i] = (val1 + frac * (val2 - val1)).toInt().toShort()
        }

        return toByteArray(outSamples)
    }

    private fun toByteArray(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        shorts.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
