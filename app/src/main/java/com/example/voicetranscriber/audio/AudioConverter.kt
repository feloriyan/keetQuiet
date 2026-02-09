package com.example.voicetranscriber.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.voicetranscriber.util.FileLogger
import com.example.voicetranscriber.util.ResourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * AudioConverter is responsible for converting various audio formats to PCM format
 * suitable for speech recognition. It supports multiple conversion strategies:
 * 
 * 1. FFmpeg conversion (primary method)
 * 2. MediaCodec fallback (for devices without FFmpeg support)
 *
 * The converter automatically handles URI resolution, temporary file management,
 * and resource cleanup. It targets a standard sample rate of 16kHz and mono channel
 * output, which is optimal for most speech recognition models.
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

    /**
     * Converts audio from the given URI to PCM format.
     *
     * @param inputUri The URI of the audio file to convert
     * @return Result containing the converted file or an exception
     */
    suspend fun convertToPcm(inputUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        logConversionStart(inputUri)

        // 1. Resolve URI to local temp file
        val tempInputFile = resolveInputUri(inputUri) ?: return@withContext createUriResolutionFailure()

        val outputFile = createOutputFile()

        return@withContext try {
            // 2. Try conversion strategies
            when {
                tryFfmpegConversion(tempInputFile, outputFile) -> {
                    logSuccess("FFmpeg conversion successful")
                    Result.success(outputFile)
                }
                tryMediaCodecConversion(tempInputFile, outputFile) -> {
                    logSuccess("MediaCodec conversion successful")
                    Result.success(outputFile)
                }
                else -> {
                    cleanupFile(outputFile)
                    Result.failure(Exception("All conversion attempts failed"))
                }
            }
        } finally {
            cleanupFile(tempInputFile)
        }
    }

    // --- Conversion Strategies ---

    private suspend fun tryFfmpegConversion(inputFile: File, outputFile: File): Boolean {
        return executeConversionStrategy(
            strategyName = "FFmpeg",
            execution = { convertWithFFmpeg(inputFile, outputFile) }
        )
    }

    private suspend fun tryMediaCodecConversion(inputFile: File, outputFile: File): Boolean {
        return executeConversionStrategy(
            strategyName = "MediaCodec",
            execution = { convertWithMediaCodec(inputFile, outputFile) }
        )
    }

    private suspend fun executeConversionStrategy(
        strategyName: String,
        execution: suspend () -> Boolean
    ): Boolean {
        return try {
            if (execution()) {
                logSuccess("$strategyName conversion successful")
                true
            } else {
                logError("$strategyName conversion failed")
                false
            }
        } catch (e: Exception) {
            logError("$strategyName conversion failed with exception", e)
            false
        }
    }

    // --- Private Helper Methods ---

    private fun resolveInputUri(inputUri: Uri): File? {
        return uriResolver.createTempFileFromUri(inputUri)?.also {
            logDebug("Successfully resolved URI to temp file: ${it.absolutePath}")
        } ?: run {
            logError("Could not resolve input URI to a valid file")
            null
        }
    }

    private fun createUriResolutionFailure(): Result<File> {
        val errorMessage = "Could not resolve input URI to a valid file"
        logError(errorMessage)
        return Result.failure(Exception(errorMessage))
    }

    // --- FFmpeg Strategy ---

    private suspend fun convertWithFFmpeg(inputFile: File, outputFile: File): Boolean {
        val command = arrayOf(
            "ffmpeg", "-i", inputFile.absolutePath,
            "-ar", TARGET_SAMPLE_RATE.toString(),
            "-ac", TARGET_CHANNELS.toString(),
            "-c:a", "pcm_s16le", "-f", "wav",
            outputFile.absolutePath, "-y"
        )

        return suspendCancellableCoroutine { continuation ->
            fileLogger.log(TAG, "Running FFmpeg: ${command.joinToString(" ")}")
            
            RxFFmpegInvoke.getInstance().runCommand(command, object : RxFFmpegSubscriber() {
                override fun onFinish() {
                    if (continuation.isActive) continuation.resume(outputFile.exists() && outputFile.length() > 0)
                }
                override fun onCancel() {
                    if (continuation.isActive) continuation.resume(false)
                }
                override fun onError(msg: String?) {
                    fileLogger.logError(TAG, "FFmpeg error: $msg")
                    if (continuation.isActive) continuation.resume(false)
                }
                override fun onProgress(p: Int, t: Long) {}
            })

            continuation.invokeOnCancellation { 
                try { RxFFmpegInvoke.getInstance().exit() } catch(e: Exception) { /* ignore */ }
            }
        }
    }

    // --- MediaCodec Strategy ---

    private suspend fun convertWithMediaCodec(inputFile: File, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)
            val trackIndex = selectAudioTrack(extractor) ?: return@withContext false

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext false

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmData = decodeAndResample(extractor, decoder, format)
            WavHelper.writeWavFile(outputFile, pcmData, TARGET_SAMPLE_RATE, TARGET_CHANNELS)

            return@withContext true
        } catch (e: Exception) {
            logError("MediaCodec conversion failed", e)
            return@withContext false
        } finally {
            // Cleanup resources safely
            try { decoder?.stop() } catch (e: Exception) { logError("Error stopping decoder", e) }
            try { decoder?.release() } catch (e: Exception) { logError("Error releasing decoder", e) }
            try { extractor.release() } catch (e: Exception) { logError("Error releasing extractor", e) }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun decodeAndResample(extractor: MediaExtractor, decoder: MediaCodec, format: MediaFormat): ByteArray {
        val pcmBuffer = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var isInputEOS = false
        var isOutputEOS = false

        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

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

                    val resampled = AudioResampler.resample(
                        chunk, srcRate, srcChannels, TARGET_SAMPLE_RATE, TARGET_CHANNELS
                    )
                    pcmBuffer.write(resampled)
                }
                decoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) isOutputEOS = true
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Handle format change if necessary, but we rely on initial format mostly
            }
        }
        return pcmBuffer.toByteArray()
    }

    // --- File Utils ---

    private fun createOutputFile(): File {
        val outputDir = File(context.cacheDir, "converted_audio").apply { mkdirs() }
        return File(outputDir, "input_${System.currentTimeMillis()}.wav")
    }

    private fun cleanupFile(file: File) {
        ResourceManager.safeDelete(file)
    }

    // --- Logging Helpers ---

    private fun logConversionStart(uri: Uri) {
        fileLogger.log(TAG, "Starting conversion for URI: $uri")
    }

    private fun logSuccess(message: String) {
        fileLogger.log(TAG, message)
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (exception != null) {
            fileLogger.logError(TAG, message, exception)
        } else {
            fileLogger.logError(TAG, message)
        }
    }

    private fun logDebug(message: String) {
        fileLogger.log(TAG, "[DEBUG] $message")
    }
}