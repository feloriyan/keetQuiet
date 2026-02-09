package com.example.voicetranscriber.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<Transcription>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcription: Transcription): Long
    
    @Delete
    suspend fun delete(transcription: Transcription)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
}
