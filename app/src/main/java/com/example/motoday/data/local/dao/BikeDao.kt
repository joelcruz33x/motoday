package com.example.motoday.data.local.dao

import androidx.room.*
import com.example.motoday.data.local.entities.BikeEntity
import com.example.motoday.data.local.entities.BikeWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface BikeDao {
    @Transaction
    @Query("SELECT * FROM garage")
    fun getAllBikesWithPhotos(): Flow<List<BikeWithPhotos>>

    @Query("SELECT * FROM garage")
    fun getAllBikes(): Flow<List<BikeEntity>>

    @Query("SELECT * FROM garage")
    suspend fun getAllBikesOnce(): List<BikeEntity>

    @Query("SELECT * FROM garage WHERE id = :bikeId")
    suspend fun getBikeById(bikeId: Int): BikeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(bike: BikeEntity)

    @Delete
    suspend fun delete(bike: BikeEntity)

    @Query("DELETE FROM garage")
    suspend fun deleteAll()
}
