package io.github.feloriyan.keetquiet.domain

import android.net.Uri
import io.github.feloriyan.keetquiet.audio.AudioConverter
import io.github.feloriyan.keetquiet.model.TranscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ProcessVoiceMessageUseCase @Inject constructor(
    private val audioConverter: AudioConverter,
    private val transcriptionManager: TranscriptionManager
) {

    suspend operator fun invoke(
        uri: Uri,
        sourceApp: String,
        originalFilename: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conversionResult = audioConverter.convertToPcm(uri)

        conversionResult.fold(
            onSuccess = { pcmFile ->
                try {
                    transcriptionManager.enqueueTranscription(
                        file = pcmFile,
                        sourceApp = sourceApp,
                        originalFilename = originalFilename
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}
