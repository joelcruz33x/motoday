package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "garage")
data class BikeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val remoteId: String? = null,
    val model: String,
    val year: String,
    val color: String,
    val specs: String,
    val status: String = "Excelente",
    val bikePictureUri: String? = null,
    val currentKm: Int = 0
)
