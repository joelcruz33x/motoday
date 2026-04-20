package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passport_stamps")
data class PassportStampEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rideId: Int,
    val rideTitle: String,
    val date: Long,
    val locationName: String,
    val iconResName: String = "ic_stamp_default"
)
