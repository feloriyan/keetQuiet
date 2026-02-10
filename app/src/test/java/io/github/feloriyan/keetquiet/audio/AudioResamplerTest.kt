package io.github.feloriyan.keetquiet.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioResamplerTest {

    @Test
    fun resample_sameFormat_returnsInputUnchanged() {
        val input = shortArrayOf(100, -200, 300, -400).toLittleEndianBytes()

        val output = AudioResampler.resample(
            input = input,
            inRate = 16000,
            inChannels = 1,
            outRate = 16000,
            outChannels = 1
        )

        assertArrayEquals(input, output)
    }

    @Test
    fun resample_stereoToMono_halvesSampleCount() {
        // 4 stereo frames (L,R) -> 4 mono frames
        val stereo = shortArrayOf(
            1000, -1000,
            2000, -2000,
            3000, -3000,
            4000, -4000
        ).toLittleEndianBytes()

        val mono = AudioResampler.resample(
            input = stereo,
            inRate = 16000,
            inChannels = 2,
            outRate = 16000,
            outChannels = 1
        )

        assertEquals(4 * 2, mono.size) // 4 mono samples, 2 bytes each
    }

    @Test
    fun resample_rateConversion_reducesOutputWhenTargetRateLower() {
        val mono = ShortArray(1600) { (it % 100).toShort() }.toLittleEndianBytes()

        val output = AudioResampler.resample(
            input = mono,
            inRate = 16000,
            inChannels = 1,
            outRate = 8000,
            outChannels = 1
        )

        // Roughly half number of samples when halving sample rate.
        assertEquals(800 * 2, output.size)
    }

    private fun ShortArray.toLittleEndianBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN)
        forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
