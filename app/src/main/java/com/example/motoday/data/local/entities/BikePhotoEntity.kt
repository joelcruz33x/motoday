package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bike_photos",
    foreignKeys = [
        ForeignKey(
            entity = BikeEntity::class,
            parentColumns = ["id"],
            childColumns = ["bikeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bikeId"])]
)
data class BikePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bikeId: Int,
    val remoteId: String? = null,
    val uri: String
)
