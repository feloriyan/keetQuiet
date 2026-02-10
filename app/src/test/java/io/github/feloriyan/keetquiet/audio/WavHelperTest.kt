package io.github.feloriyan.keetquiet.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHelperTest {

    @Test
    fun writeWavFile_writesValidHeaderAndPayloadSize() {
        val temp = File.createTempFile("wav-helper-test", ".wav")
        temp.deleteOnExit()
        val pcm = ByteArray(320) { (it % 64).toByte() }

        WavHelper.writeWavFile(
            file = temp,
            pcmData = pcm,
            sampleRate = 16000,
            channels = 1
        )

        val bytes = temp.readBytes()
        assertTrue(bytes.size >= 44)

        val riff = String(bytes.copyOfRange(0, 4))
        val wave = String(bytes.copyOfRange(8, 12))
        val data = String(bytes.copyOfRange(36, 40))

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("data", data)

        val dataSize = ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(pcm.size, dataSize)
        assertEquals(44 + pcm.size, bytes.size)
    }
}
