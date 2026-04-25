package com.example.motoday.data.local.dao

import androidx.room.*
import com.example.motoday.data.local.entities.BikePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BikePhotoDao {
    @Query("SELECT * FROM bike_photos WHERE bikeId = :bikeId")
    fun getPhotosForBike(bikeId: Int): Flow<List<BikePhotoEntity>>

    @Query("SELECT * FROM bike_photos WHERE bikeId = :bikeId")
    suspend fun getPhotosForBikeOnce(bikeId: Int): List<BikePhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: BikePhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: BikePhotoEntity)

    @Query("DELETE FROM bike_photos WHERE bikeId = :bikeId")
    suspend fun deletePhotosForBike(bikeId: Int)
}
