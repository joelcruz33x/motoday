package com.example.motoday.data.local.dao

import androidx.room.*
import com.example.motoday.data.local.entities.RideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY date ASC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun getRideById(id: Int): RideEntity?

    @Query("SELECT * FROM rides WHERE id = :id")
    fun getRideByIdFlow(id: Int): Flow<RideEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Query("SELECT * FROM rides WHERE status = :status")
    fun getRidesByStatus(status: String): Flow<List<RideEntity>>

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Delete
    suspend fun deleteRide(ride: RideEntity)

    @Query("DELETE FROM rides WHERE status = 'COMPLETED' AND completedAt IS NOT NULL AND completedAt < :threshold")
    suspend fun cleanupOldRides(threshold: Long)
}
