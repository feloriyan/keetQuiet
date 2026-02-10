package io.github.feloriyan.keetquiet.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val transcriptionDao: TranscriptionDao
) {
    val allTranscriptions: Flow<List<Transcription>> = transcriptionDao.getAllTranscriptions()

    suspend fun insert(transcription: Transcription): Long {
        return transcriptionDao.insert(transcription)
    }

    suspend fun delete(transcription: Transcription) {
        transcriptionDao.delete(transcription)
    }
}
