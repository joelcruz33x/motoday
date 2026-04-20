package com.example.motoday.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.motoday.data.local.entities.MaintenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<MaintenanceEntity>>

    @Insert
    suspend fun insertLog(log: MaintenanceEntity)

    @Query("DELETE FROM maintenance_logs WHERE id = :id")
    suspend fun deleteLog(id: Int)
}
