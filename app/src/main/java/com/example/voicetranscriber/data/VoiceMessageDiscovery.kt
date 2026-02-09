package com.example.voicetranscriber.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceMessageDiscovery scans the device's audio storage to find voice messages
 * from popular messaging apps (WhatsApp, Telegram, Signal) and other sources.
 *
 * The discovery process includes:
 * 1. Querying MediaStore for all audio files
 * 2. Filtering by file size and naming patterns
 * 3. Determining the source app based on file paths
 * 4. Removing duplicates and sorting by date
 *
 * This class handles all the complexity of content resolver queries and
 * provides a clean list of voice messages ready for transcription.
 */
@Singleton
class VoiceMessageDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileLogger: com.example.voicetranscriber.util.FileLogger
) {
    companion object {
        private const val TAG = "VoiceMessageDiscovery"
        private const val MIN_FILE_SIZE_BYTES = 1024L
    }

    /**
     * Discovers voice messages from the device's audio storage.
     *
     * @return List of discovered voice messages sorted by date (newest first)
     */
    suspend fun discoverVoiceMessages(): List<VoiceMessage> = withContext(Dispatchers.IO) {
        logDiscoveryStart()
        
        val messages = queryAudioFilesFromAllSources()
        
        logDiscoveryResults(messages)
        
        return@withContext filterAndSortMessages(messages)
    }

    // --- Query Methods ---

    private fun queryAudioFilesFromAllSources(): List<VoiceMessage> {
        val messages = mutableListOf<VoiceMessage>()
        val queryUris = getQueryUris()
        val projection = getAudioProjection()
        
        queryUris.forEach { uri ->
            logQueryStart(uri)
            queryAudioFiles(uri, projection, messages)
        }
        
        return messages
    }

    private fun getQueryUris(): List<android.net.Uri> {
        return listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )
    }

    private fun getAudioProjection(): Array<String> {
        return arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
    }

    private fun queryAudioFiles(
        uri: Uri,
        projection: Array<String>,
        messages: MutableList<VoiceMessage>
    ) {
        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                processQueryResults(cursor, uri, messages)
            }
        } catch (e: Exception) {
            logError("Error querying audio files from $uri", e)
        }
    }

    private fun processQueryResults(
        cursor: android.database.Cursor,
        queryUri: Uri,
        messages: MutableList<VoiceMessage>
    ) {
        logCursorInfo(cursor)
        
        val columnIndices = getColumnIndices(cursor)
        
        while (cursor.moveToNext()) {
            processAudioFile(cursor, queryUri, columnIndices, messages)
        }
    }

    private fun getColumnIndices(cursor: android.database.Cursor): ColumnIndices {
        return ColumnIndices(
            idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
            nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME),
            durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
            dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
            sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
            relativePathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
        )
    }

    private fun processAudioFile(
        cursor: android.database.Cursor,
        queryUri: Uri,
        columnIndices: ColumnIndices,
        messages: MutableList<VoiceMessage>
    ) {
        val audioData = extractAudioData(cursor, queryUri, columnIndices)
        
        if (isValidVoiceMessage(audioData)) {
            val voiceMessage = createVoiceMessage(audioData)
            messages.add(voiceMessage)
        }
    }

    private fun extractAudioData(
        cursor: android.database.Cursor,
        queryUri: Uri,
        columnIndices: ColumnIndices
    ): AudioData {
        val relativePath = if (columnIndices.relativePathColumn >= 0) {
            cursor.getString(columnIndices.relativePathColumn)
        } else {
            null
        }

        return AudioData(
            id = cursor.getLong(columnIndices.idColumn),
            name = cursor.getString(columnIndices.nameColumn),
            duration = cursor.getLong(columnIndices.durationColumn),
            date = cursor.getLong(columnIndices.dateColumn) * 1000,
            size = cursor.getLong(columnIndices.sizeColumn),
            sourceHint = relativePath,
            uri = ContentUris.withAppendedId(
                queryUri,
                cursor.getLong(columnIndices.idColumn)
            )
        )
    }

    private fun isValidVoiceMessage(audioData: AudioData): Boolean {
        return audioData.size > MIN_FILE_SIZE_BYTES && 
               isLikelyVoiceNote(audioData)
    }

    private fun isLikelyVoiceNote(audioData: AudioData): Boolean {
        val source = determineSource(audioData.sourceHint)
        return source != "Other" || 
               audioData.name.contains("voice", ignoreCase = true) || 
               audioData.name.contains("audio", ignoreCase = true) ||
               audioData.name.contains("ptt", ignoreCase = true)
    }

    private fun determineSource(sourceHint: String?): String {
        val hint = sourceHint ?: ""
        return when {
            hint.contains("com.whatsapp", ignoreCase = true) ||
                   hint.contains("WhatsApp", ignoreCase = true) -> "WhatsApp"
            hint.contains("Telegram", ignoreCase = true) ||
                   hint.contains("org.telegram.messenger", ignoreCase = true) -> "Telegram"
            hint.contains("Signal", ignoreCase = true) ||
                   hint.contains("org.thoughtcrime.securesms", ignoreCase = true) -> "Signal"
            else -> "Other"
        }
    }

    private fun createVoiceMessage(audioData: AudioData): VoiceMessage {
        return VoiceMessage(
            uri = audioData.uri,
            name = audioData.name,
            duration = audioData.duration,
            timestamp = audioData.date,
            source = determineSource(audioData.sourceHint),
            size = audioData.size
        )
    }

    // --- Filtering and Sorting ---

    private fun filterAndSortMessages(messages: List<VoiceMessage>): List<VoiceMessage> {
        return messages
            .distinctBy { it.uri.toString() }
            .sortedByDescending { it.timestamp }
            .also { logFilterResults(messages.size, it.size) }
    }

    // --- Logging Helpers ---

    private fun logDiscoveryStart() {
        fileLogger.log(TAG, "Starting voice message discovery...")
    }

    private fun logQueryStart(uri: android.net.Uri) {
        Log.d(TAG, "Querying URI: $uri")
    }

    private fun logCursorInfo(cursor: android.database.Cursor) {
        fileLogger.log(TAG, "Found ${cursor.count} audio files in current query")
    }

    private fun logDiscoveryResults(messages: List<VoiceMessage>) {
        fileLogger.log(TAG, "Discovery finished. Total found: ${messages.size}")
    }

    private fun logFilterResults(totalFound: Int, distinctCount: Int) {
        fileLogger.log(TAG, "Filtered results: $distinctCount distinct messages from $totalFound total")
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (exception != null) {
            fileLogger.logError(TAG, message, exception)
        } else {
            fileLogger.logError(TAG, message)
        }
    }

    // --- Data Classes ---

    private data class ColumnIndices(
        val idColumn: Int,
        val nameColumn: Int,
        val durationColumn: Int,
        val dateColumn: Int,
        val sizeColumn: Int,
        val relativePathColumn: Int
    )

    private data class AudioData(
        val id: Long,
        val name: String,
        val duration: Long,
        val date: Long,
        val size: Long,
        val sourceHint: String?,
        val uri: Uri
    )
}
