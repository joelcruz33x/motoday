package com.example.motoday.data.remote

import android.content.Context
import io.appwrite.Client
import io.appwrite.Query
import io.appwrite.models.Document
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage

class AppwriteManager private constructor(context: Context) {
    companion object {
        const val DATABASE_ID = "69e81a9500157d642919"
        const val COLLECTION_PROFILES_ID = "profiles"
        const val COLLECTION_POSTS_ID = "posts"
        const val COLLECTION_STAMPS_ID = "stamps"
        const val BUCKET_ID = "69e844ea000bbe88673c"

        @Volatile
        private var INSTANCE: AppwriteManager? = null

        fun getInstance(context: Context): AppwriteManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AppwriteManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
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

    suspend fun getPosts(): List<Document<Map<String, Any>>> {
        return databases.listDocuments(
            databaseId = DATABASE_ID,
            collectionId = COLLECTION_POSTS_ID,
            queries = listOf(
                Query.orderDesc("timestamp")
            )
        ).documents
    }

    suspend fun toggleLike(postId: String, userId: String, currentLikes: List<String>): List<String>? {
        return try {
            val newLikes = if (currentLikes.contains(userId)) {
                currentLikes.filter { it != userId }
            } else {
                currentLikes + userId
            }

            databases.updateDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_POSTS_ID,
                documentId = postId,
                data = mapOf("likes" to newLikes)
            )
            newLikes
        } catch (e: Exception) {
            e.printStackTrace()
            null // Devolvemos null si falla para que el UI sepa que no hubo cambio
        }
    }

    suspend fun createPost(
        userId: String,
        userName: String,
        userLevel: String,
        profilePic: String?,
        imageUrls: List<String>, // Cambiado a lista
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
                "imageUrl" to imageUrls, // Appwrite recibirá el array
                "caption" to caption,
                "timestamp" to timestamp
            )
        )
    }

    // --- SECCIÓN DE SELLOS (PASAPORTE) ---

    suspend fun syncStamp(
        userId: String,
        rideId: Int,
        rideTitle: String,
        locationName: String,
        iconResName: String,
        date: Long
    ) {
        try {
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STAMPS_ID,
                documentId = io.appwrite.ID.unique(),
                data = mapOf(
                    "userId" to userId,
                    "rideId" to rideId.toLong(), // Aseguramos formato numérico
                    "rideTitle" to rideTitle,
                    "locationName" to locationName,
                    "iconResName" to iconResName,
                    "date" to date
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("AppwriteSync", "Error al sincronizar sello: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun getUserStamps(userId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STAMPS_ID,
                queries = listOf(
                    Query.equal("userId", userId)
                )
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }
}
