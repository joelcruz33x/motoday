package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val content: String,
    val timestamp: Long,
    val isSynced: Boolean = false,
    val imageUris: String // Comma separated list or JSON string
)
