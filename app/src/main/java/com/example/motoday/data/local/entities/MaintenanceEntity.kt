package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenance_logs")
data class MaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val mileage: Int,
    val type: String, // e.g., "Cambio de Aceite", "Frenos", "General"
    val description: String,
    val cost: Double = 0.0
)
