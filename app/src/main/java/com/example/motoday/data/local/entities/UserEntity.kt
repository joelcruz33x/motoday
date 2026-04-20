package com.example.motoday.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserEntity(
    @PrimaryKey val id: Int = 1, // ID fijo para el único perfil de usuario local
    val name: String,
    val level: String,
    val profilePictureUri: String? = null,
    val bikePictureUri: String? = null,
    val bikeModel: String,
    val bikeSpecs: String,
    val bikeYear: String,
    val bikeColor: String,
    val bikeStatus: String = "Excelente",
    val totalKilometers: Int = 0,
    val medalsCount: Int = 0,
    val ridesCompleted: Int = 0,
    val useMiles: Boolean = false,
    val notificationsEnabled: Boolean = true
)
