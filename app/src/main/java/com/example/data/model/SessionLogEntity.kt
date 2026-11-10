package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val tag: String = "General",
    val sessionType: String, // "WORK", "SHORT_BREAK", "LONG_BREAK"
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String // e.g. "2026-10-22"
)
