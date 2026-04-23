package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_stories")
data class SeenStoryEntity(
    @PrimaryKey val storyId: String,
    val seenAt: Long = System.currentTimeMillis()
)