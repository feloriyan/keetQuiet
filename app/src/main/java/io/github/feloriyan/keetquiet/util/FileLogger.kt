package io.github.feloriyan.keetquiet.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val logFile: File by lazy {
        // Use external files dir to make it accessible via /sdcard/Android/data/...
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, "detailed_log.txt")
    }

    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        writeAsync(formatLogEntry(tag, message))
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val timestamp = timestampFormatter.format(Date())
        val stackTrace = throwable?.stackTraceToString() ?: ""
        val logEntry = "$timestamp [$tag] ERROR: $message\n$stackTrace\n"
        writeAsync(logEntry)
    }
    
    fun getLogFilePath(): String = logFile.absolutePath
    
    fun clearLog() {
        scope.launch {
            writeMutex.withLock {
                try {
                    if (logFile.exists()) {
                        logFile.delete()
                    }
                    logFile.createNewFile()
                } catch (e: Exception) {
                    Log.e("FileLogger", "Failed to clear log", e)
                }
            }
        }
    }

    private fun writeAsync(logEntry: String) {
        scope.launch {
            writeMutex.withLock {
                try {
                    if (!logFile.exists()) {
                        logFile.createNewFile()
                    }
                    FileWriter(logFile, true).use { writer ->
                        writer.append(logEntry)
                    }
                } catch (e: Exception) {
                    Log.e("FileLogger", "Failed to write log", e)
                }
            }
        }
    }

    private fun formatLogEntry(tag: String, message: String): String {
        val timestamp = timestampFormatter.format(Date())
        return "$timestamp [$tag] $message\n"
    }
}
