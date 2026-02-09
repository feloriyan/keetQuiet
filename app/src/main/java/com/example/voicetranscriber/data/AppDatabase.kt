package com.example.voicetranscriber.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Transcription::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
}
