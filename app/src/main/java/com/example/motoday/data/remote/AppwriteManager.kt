package com.example.motoday.data.remote

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage

class AppwriteManager(context: Context) {
    companion object {
        const val DATABASE_ID = "69e81a9500157d642919"
        const val COLLECTION_PROFILES_ID = "profiles"
        const val COLLECTION_POSTS_ID = "posts"
        const val BUCKET_ID = "69e844ea000bbe88673c"
    }

    val client = Client(context)
        .setEndpoint("https://nyc.cloud.appwrite.io/v1")
        .setProject("69e6836b00267f431c20")
        .setSelfSigned(true)

    val account = Account(client)
    val databases = Databases(client)
    val storage = Storage(client)

    suspend fun uploadImage(file: io.appwrite.models.InputFile): String {
        val response = storage.createFile(
            bucketId = BUCKET_ID,
            fileId = io.appwrite.ID.unique(),
            file = file
        )
        return response.id
    }

    fun getImageUrl(fileId: String): String {
        return "https://nyc.cloud.appwrite.io/v1/storage/buckets/$BUCKET_ID/files/$fileId/view?project=69e6836b00267f431c20"
    }

    suspend fun updateUserProfile(
        userId: String,
        name: String,
        level: String,
        bikeModel: String,
        bikeSpecs: String,
        bikeYear: String,
        bikeColor: String,
        profilePic: String? = null
    ) {
        val data = mutableMapOf(
            "name" to name,
            "level" to level,
            "bikeModel" to bikeModel,
            "bikeSpecs" to bikeSpecs,
            "bikeYear" to bikeYear,
            "bikeColor" to bikeColor
        )
        profilePic?.let { data["profilePic"] = it }

        databases.updateDocument(
            databaseId = DATABASE_ID,
            collectionId = COLLECTION_PROFILES_ID,
            documentId = userId,
            data = data
        )
    }

    suspend fun createPost(
        userId: String,
        userName: String,
        userLevel: String,
        profilePic: String?,
        imageUrl: String,
        caption: String,
        timestamp: Long
    ) {
        databases.createDocument(
            databaseId = DATABASE_ID,
            collectionId = COLLECTION_POSTS_ID,
            documentId = io.appwrite.ID.unique(),
            data = mapOf(
                "userId" to userId,
                "userName" to userName,
                "userLevel" to userLevel,
                "profilePic" to (profilePic ?: ""),
                "imageUrl" to imageUrl,
                "caption" to caption,
                "timestamp" to timestamp
            )
        )
    }
}
