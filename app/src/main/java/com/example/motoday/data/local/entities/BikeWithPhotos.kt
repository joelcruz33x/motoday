package com.example.motoday.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class BikeWithPhotos(
    @Embedded val bike: BikeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bikeId"
    )
    val photos: List<BikePhotoEntity>
)
