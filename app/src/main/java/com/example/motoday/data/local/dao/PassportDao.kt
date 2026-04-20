package com.example.motoday.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.motoday.data.local.entities.PassportStampEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PassportDao {
    @Query("SELECT * FROM passport_stamps ORDER BY date DESC")
    fun getAllStamps(): Flow<List<PassportStampEntity>>

    @Insert
    suspend fun insertStamp(stamp: PassportStampEntity)

    @Query("SELECT COUNT(*) FROM passport_stamps WHERE rideId = :rideId")
    suspend fun hasStampForRide(rideId: Int): Int
}
