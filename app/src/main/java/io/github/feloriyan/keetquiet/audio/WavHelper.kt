package io.github.feloriyan.keetquiet.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavHelper {
    fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val header = buildWavHeader(pcmData.size, sampleRate, channels)
        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }

    private fun buildWavHeader(dataSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * 2

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size (16 for PCM)
            putShort(1) // AudioFormat (1 for PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort()) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
    }
}
