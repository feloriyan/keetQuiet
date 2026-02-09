package com.example.voicetranscriber.data

import android.net.Uri

data class VoiceMessage(
    val uri: Uri,
    val name: String,
    val duration: Long, // in milliseconds
    val timestamp: Long,
    val source: String, // e.g., "WhatsApp", "Telegram", "Signal", "Other"
    val size: Long
)
