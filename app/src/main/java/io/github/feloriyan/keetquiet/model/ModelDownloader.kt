package io.github.feloriyan.keetquiet.model

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileLogger: io.github.feloriyan.keetquiet.util.FileLogger
) {
    companion object {
        private const val TAG = "ModelDownloader"
        private const val HF_REPO_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/"
        
        private val FILE_MAPPING = mapOf(
            "encoder.int8.onnx" to "encoder.int8.onnx",
            "decoder.int8.onnx" to "decoder.int8.onnx", 
            "joiner.int8.onnx" to "joiner.int8.onnx",
            "tokens.txt" to "tokens.txt"
        )
        
        private val FILE_HASHES = mapOf(
            "encoder.int8.onnx" to "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247", 
            "decoder.int8.onnx" to "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
            "joiner.int8.onnx" to "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
            "tokens.txt" to "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"
        )
        
        val REQUIRED_FILES = FILE_MAPPING.keys.toList()
    }
    
    // Using a shared directory in Documents to allow sharing across our apps
    private val modelDir: File
        get() = File(context.filesDir, "STTModels")
    
    fun areModelsAvailable(): Boolean {
        return try {
            if (!modelDir.exists()) return false
            REQUIRED_FILES.all { filename -> isModelFileValid(filename) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking models", e)
            false
        }
    }
    
    fun getModelDirectory(): String = modelDir.absolutePath
    
    fun ensureModelsAvailable(): Flow<DownloadState> = flow {
        emit(DownloadState.Checking)
        if (areModelsAvailable()) {
            emit(DownloadState.Complete)
            return@flow
        }
        
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            emit(DownloadState.Error("Could not create model directory at ${modelDir.absolutePath}."))
            return@flow
        }
        
        emit(DownloadState.Downloading(0))
        var attempts = 0
        while (attempts < 3) {
            try {
                downloadModels { progress ->
                    emit(DownloadState.Downloading(progress))
                }
                emit(DownloadState.Complete)
                return@flow
            } catch (e: Exception) {
                attempts++
                if (attempts >= 3) {
                    Log.e(TAG, "Download failed after 3 attempts", e)
                    emit(DownloadState.Error(e.message ?: "Download failed"))
                    return@flow
                }
                Log.w(TAG, "Download failed (attempt $attempts), retrying...", e)
                fileLogger.log(TAG, "Download failed (attempt $attempts), retrying in 1s...")
                kotlinx.coroutines.delay(1000)
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private suspend fun downloadModels(onProgress: suspend (Int) -> Unit) {
        val fileSizes = mapOf(
            "encoder.int8.onnx" to 652184281L,
            "decoder.int8.onnx" to 11845275L, 
            "joiner.int8.onnx" to 6355277L,
            "tokens.txt" to 93939L
        )
        val totalSize = fileSizes.values.sum()
        var totalBytesDownloaded = 0L

        // Pre-calculate already existing files
        REQUIRED_FILES.forEach { filename ->
            if (isModelFileValid(filename)) {
                totalBytesDownloaded += fileSizes[filename] ?: 0L
            }
        }

        FILE_MAPPING.entries.forEach { entry ->
            val localName = entry.key
            val remoteName = entry.value
            val outFile = File(modelDir, localName)
            if (isModelFileValid(localName)) return@forEach
            if (outFile.exists()) {
                fileLogger.log(TAG, "Deleting invalid or incomplete model file: $localName")
                outFile.delete()
            }

            fileLogger.log(TAG, "Downloading $localName from $remoteName")
            val url = URL("$HF_REPO_URL$remoteName")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            try {
                val tempFile = File(modelDir, "$localName.tmp")
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(65536) // Larger buffer for faster downloads
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesDownloaded += bytesRead
                            val progress = ((totalBytesDownloaded.toDouble() / totalSize) * 100).toInt()
                            if (totalBytesDownloaded % (1024 * 1024) < 16384) { 
                                 onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }
                if (tempFile.renameTo(outFile)) {
                    fileLogger.log(TAG, "Successfully downloaded and renamed to $localName")
                } else {
                    val msg = "Failed to rename tmp file to $localName"
                    fileLogger.logError(TAG, msg)
                    throw java.io.IOException(msg)
                }
            } catch (e: Exception) {
                fileLogger.logError(TAG, "Error downloading $localName", e)
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun verifyFileHash(file: File, expectedHash: String): Boolean {
        return ModelFileIntegrity.isValid(file, expectedHash)
    }

    private fun isModelFileValid(filename: String): Boolean {
        val file = File(modelDir, filename)
        if (!file.exists() || file.length() <= 0) return false
        val expectedHash = FILE_HASHES[filename]
        return if (expectedHash.isNullOrBlank()) {
            true
        } else {
            verifyFileHash(file, expectedHash)
        }
    }
}

sealed class DownloadState {
    object Checking : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}
