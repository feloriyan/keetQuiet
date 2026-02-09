package com.example.voicetranscriber.util

import android.util.Log

/**
 * Utility class for managing resources with proper cleanup.
 * Provides safe resource handling patterns to prevent memory leaks.
 */
object ResourceManager {

    /**
     * Safely closes an AutoCloseable resource, logging any errors that occur during closing.
     *
     * @param resource The resource to close
     * @param tag The log tag to use for error logging
     * @param resourceName The name of the resource for error messages
     */
    fun safeClose(resource: AutoCloseable?, tag: String, resourceName: String) {
        try {
            resource?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing $resourceName", e)
        }
    }

    /**
     * Safely releases a releasable resource, logging any errors that occur during release.
     *
     * @param releasable The resource to release
     * @param tag The log tag to use for error logging
     * @param resourceName The name of the resource for error messages
     */
    fun safeRelease(releasable: Releasable?, tag: String, resourceName: String) {
        try {
            releasable?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing $resourceName", e)
        }
    }

    /**
     * Safely stops and releases a media codec or similar resource.
     *
     * @param codec The codec to stop and release
     * @param tag The log tag to use for error logging
     * @param resourceName The name of the resource for error messages
     */
    fun safeStopAndRelease(codec: StopReleasable?, tag: String, resourceName: String) {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping and releasing $resourceName", e)
        }
    }

    /**
     * Safely deletes a file, ignoring any errors that occur during deletion.
     *
     * @param file The file to delete
     */
    fun safeDelete(file: java.io.File?) {
        try {
            if (file?.exists() == true) {
                file.delete()
            }
        } catch (e: Exception) {
            // Ignore file deletion errors
        }
    }

    /**
     * Interface for resources that can be released.
     */
    interface Releasable {
        fun release()
    }

    /**
     * Interface for resources that can be stopped and released.
     */
    interface StopReleasable : Releasable {
        fun stop()
    }

    /**
     * Executes a block of code and ensures proper cleanup of resources.
     *
     * @param block The code block to execute
     * @param cleanup The cleanup function to call after execution
     * @return The result of the block execution
     */
    inline fun <T> withResourceCleanup(block: () -> T, cleanup: () -> Unit): T {
        try {
            return block()
        } finally {
            cleanup()
        }
    }

    /**
     * Executes a block of code with multiple resources and ensures proper cleanup.
     *
     * @param block The code block to execute with the resources
     * @param resources The resources to manage
     * @return The result of the block execution
     */
    inline fun <T, R> withMultipleResources(
        resources: List<R>,
        cleanup: (R) -> Unit,
        block: (List<R>) -> T
    ): T {
        try {
            return block(resources)
        } finally {
            resources.forEach { cleanup(it) }
        }
    }
}