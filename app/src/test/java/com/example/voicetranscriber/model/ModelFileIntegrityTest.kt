package com.example.voicetranscriber.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelFileIntegrityTest {

    @Test
    fun isValid_returnsFalse_forMissingFile() {
        val missing = File("build/non-existent-file-${System.currentTimeMillis()}.bin")
        assertFalse(ModelFileIntegrity.isValid(missing, null))
    }

    @Test
    fun isValid_returnsTrue_whenHashMatches() {
        val file = createTempBinary("hello-world")
        val hash = ModelFileIntegrity.sha256(file)
        assertNotNull(hash)
        assertTrue(ModelFileIntegrity.isValid(file, hash))
    }

    @Test
    fun isValid_returnsFalse_whenHashDoesNotMatch() {
        val file = createTempBinary("hello-world")
        assertFalse(ModelFileIntegrity.isValid(file, "deadbeef"))
    }

    @Test
    fun sha256_isDeterministic() {
        val file = createTempBinary("same-content")
        val hash1 = ModelFileIntegrity.sha256(file)
        val hash2 = ModelFileIntegrity.sha256(file)
        assertEquals(hash1, hash2)
    }

    private fun createTempBinary(content: String): File {
        val file = File.createTempFile("model-integrity-test", ".bin")
        file.deleteOnExit()
        file.writeText(content)
        return file
    }
}
