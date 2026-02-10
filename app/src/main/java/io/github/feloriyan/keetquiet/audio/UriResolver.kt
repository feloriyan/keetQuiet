package io.github.feloriyan.keetquiet.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UriResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UriResolver"
    }

    fun createTempFileFromUri(uri: Uri): File? {
        val extension = detectExtension(uri) ?: "ogg" // Fallback
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.$extension")
        tempFile.deleteOnExit()

        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: run {
                Log.e(TAG, "openInputStream returned null for URI: $uri")
                tempFile.delete()
                false
            }

            if (copied && tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                try { tempFile.delete() } catch (_: Exception) {}
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp file", e)
            try { tempFile.delete() } catch (_: Exception) {}
            null
        }
    }

    private fun detectExtension(uri: Uri): String? {
        // 1. Try filename
        var extension: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) {
                            val dotIndex = name.lastIndexOf('.')
                            if (dotIndex >= 0 && dotIndex < name.length - 1) {
                                extension = name.substring(dotIndex + 1).lowercase()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query filename", e)
        }

        if (extension != null) return extension

        // 2. Try MIME type
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                return when {
                    mimeType.contains("ogg") -> "ogg"
                    mimeType.contains("opus") -> "opus"
                    mimeType.contains("mp4") || mimeType.contains("audio/mp4") -> "m4a"
                    mimeType.contains("aac") -> "aac"
                    mimeType.contains("amr") -> "amr"
                    mimeType.contains("wav") -> "wav"
                    mimeType.contains("flac") -> "flac"
                    mimeType.contains("mpeg") -> "mp3"
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to determine MIME type", e)
        }

        // 3. Fallback/Heuristics
        if (uri.authority?.contains("whatsapp", ignoreCase = true) == true) {
            return "opus"
        }

        return null
    }
}
