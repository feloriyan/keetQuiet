package io.github.feloriyan.keetquiet.model

import java.io.File
import java.security.MessageDigest

object ModelFileIntegrity {

    fun isValid(file: File, expectedHash: String?): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        if (expectedHash.isNullOrBlank()) return true

        val actualHash = sha256(file) ?: return false
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    fun sha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
