package com.example.voicetranscriber.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class Transcription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val sourceApp: String,
    val originalFilename: String,
    val timestamp: Long,
    val duration: Float = 0f
)
