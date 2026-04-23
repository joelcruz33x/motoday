package com.example.motoday.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SeenStoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsSeen(seenStory: com.example.motoday.data.local.entities.SeenStoryEntity)

    @Query("SELECT storyId FROM seen_stories")
    fun getSeenStoryIds(): Flow<List<String>>
    
    @Query("SELECT EXISTS(SELECT 1 FROM seen_stories WHERE storyId = :storyId)")
    suspend fun isStorySeen(storyId: String): Boolean
}