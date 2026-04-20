package com.example.motoday.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.motoday.data.local.entities.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE expiresAt > :currentTime ORDER BY timestamp ASC")
    fun getActiveStories(currentTime: Long): Flow<List<StoryEntity>>

    @Insert
    suspend fun insertStory(story: StoryEntity)

    @Query("DELETE FROM stories WHERE expiresAt <= :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long)
}
