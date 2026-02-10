package io.github.feloriyan.keetquiet.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.feloriyan.keetquiet.util.FileLogger
import io.github.feloriyan.keetquiet.util.ResourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioConverter converts various audio formats to PCM (WAV) format using Android's native MediaCodec API.
 * This implementation completely replaces the previous FFmpeg-based solution to comply with F-Droid policies
 * regarding binary blobs and patented codecs.
 *
 * Supported formats (via MediaExtractor/MediaCodec): MP3, AAC/M4A, OGG/OPUS, FLAC, WAV.
 * Target output: 16kHz, 16-bit Mono PCM (WAV container).
 */
@Singleton
class AudioConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileLogger: FileLogger,
    private val uriResolver: UriResolver
) {
    companion object {
        private const val TAG = "AudioConverter"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1
        private const val TIMEOUT_US = 10000L
    }

    suspend fun convertToPcm(inputUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        logConversionStart(inputUri)

        val tempInputFile = resolveInputUri(inputUri) ?: return@withContext createUriResolutionFailure()
        val outputFile = createOutputFile()

        return@withContext try {
            if (convertWithMediaCodec(tempInputFile, outputFile)) {
                logSuccess("MediaCodec conversion successful")
                Result.success(outputFile)
            } else {
                cleanupFile(outputFile)
                Result.failure(Exception("MediaCodec conversion failed"))
            }
        } catch (e: Exception) {
            cleanupFile(outputFile)
            logError("Conversion exception", e)
            Result.failure(e)
        } finally {
            cleanupFile(tempInputFile)
        }
    }

    private suspend fun convertWithMediaCodec(inputFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        
        try {
            extractor.setDataSource(inputFile.absolutePath)
            val trackIndex = selectAudioTrack(extractor) ?: run {
                logError("No audio track found in file")
                return false
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false

            logDebug("Found audio track: $mime, SampleRate: ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}")

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmData = decodeAndResample(extractor, decoder, format)
            if (pcmData.isEmpty()) {
                logError("Decoded PCM data is empty")
                return false
            }

            WavHelper.writeWavFile(outputFile, pcmData, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
            return true

        } catch (e: Exception) {
            logError("MediaCodec processing error", e)
            return false
        } finally {
            try { decoder?.stop() } catch (e: Exception) { }
            try { decoder?.release() } catch (e: Exception) { }
            try { extractor.release() } catch (e: Exception) { }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun decodeAndResample(extractor: MediaExtractor, decoder: MediaCodec, inputFormat: MediaFormat): ByteArray {
        val pcmBuffer = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var isInputEOS = false
        var isOutputEOS = false
        
        val srcRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) 
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        while (!isOutputEOS) {
            if (!isInputEOS) {
                val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                    val chunk = ByteArray(bufferInfo.size)
                    buffer.get(chunk)
                    buffer.clear()
                    
                    if (bufferInfo.size > 0) {
                        val resampled = AudioResampler.resample(
                            chunk, srcRate, srcChannels, TARGET_SAMPLE_RATE, TARGET_CHANNELS
                        )
                        pcmBuffer.write(resampled)
                    }
                }
                decoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isOutputEOS = true
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                 // Format changed, ideally update srcRate/srcChannels if needed, 
                 // but usually MediaExtractor format is sufficient for initial config
                 val newFormat = decoder.outputFormat
                 logDebug("Output format changed: $newFormat")
            }
        }
        return pcmBuffer.toByteArray()
    }

    private fun resolveInputUri(inputUri: Uri): File? {
        return uriResolver.createTempFileFromUri(inputUri)?.also {
            logDebug("Resolved URI to: ${it.absolutePath}")
        }
    }

    private fun createUriResolutionFailure() = Result.failure<File>(Exception("Could not resolve URI"))

    private fun createOutputFile(): File {
        val outputDir = File(context.cacheDir, "converted_audio").apply { mkdirs() }
        return File(outputDir, "input_${System.currentTimeMillis()}.wav")
    }

    private fun cleanupFile(file: File) {
        ResourceManager.safeDelete(file)
    }

    private fun logConversionStart(uri: Uri) = fileLogger.log(TAG, "Starting conversion for: $uri")
    private fun logSuccess(msg: String) = fileLogger.log(TAG, msg)
    private fun logError(msg: String, e: Exception? = null) = if (e != null) fileLogger.logError(TAG, msg, e) else fileLogger.logError(TAG, msg)
    private fun logDebug(msg: String) = fileLogger.log(TAG, "[DEBUG] $msg")
}