package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val date: Long,
    val startLocation: String,
    val endLocation: String,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val meetingPoint: String,
    val scheduledStops: String = "",
    val status: String = "PLANNED", // PLANNED, ONGOING, COMPLETED
    val creatorName: String = "Joel Motero",
    val isAttending: Boolean = false,
    val participantsCount: Int = 0,
    val isSynced: Boolean = false,
    val difficulty: String = "Fácil", // Fácil, Intermedio, Difícil
    val terrainType: String = "Asfalto", // Asfalto, Mixto, Off-road
    val completedAt: Long? = null, // Timestamp de cuando se finalizó la ruta
    val remoteId: String? = null, // ID del documento en Appwrite
    val creatorId: String? = null // ID del usuario que creó la ruta en Appwrite
)
