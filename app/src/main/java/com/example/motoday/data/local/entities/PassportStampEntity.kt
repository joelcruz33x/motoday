package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "passport_stamps",
    indices = [Index(value = ["rideRemoteId", "locationName"], unique = true)]
)
data class PassportStampEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rideRemoteId: String, // Usamos el ID de Appwrite para que sea global
    val rideTitle: String,
    val date: Long,
    val locationName: String,
    val iconResName: String = "ic_stamp_default"
)
