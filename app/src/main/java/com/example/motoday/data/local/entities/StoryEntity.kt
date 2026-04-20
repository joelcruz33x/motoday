package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val userProfilePic: String?,
    val imageUri: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (6 * 60 * 60 * 1000) // 6 horas
)
