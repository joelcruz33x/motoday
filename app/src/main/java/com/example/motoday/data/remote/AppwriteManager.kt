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
        const val COLLECTION_STORIES_ID = "stories"
        const val COLLECTION_GROUPS_ID = "groups"
        const val COLLECTION_MESSAGES_ID = "group_messages"
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

    suspend fun getUserProfile(userId: String): Document<Map<String, Any>>? {
        return try {
            databases.getDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_PROFILES_ID,
                documentId = userId
            )
        } catch (e: Exception) {
            null
        }
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

    // --- SECCIÓN DE GRUPOS ---

    suspend fun createGroup(
        adminId: String,
        name: String,
        description: String,
        iconResName: String = "ic_group_default",
        photoUrl: String? = null
    ): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                documentId = io.appwrite.ID.unique(),
                data = mapOf(
                    "name" to name,
                    "description" to description,
                    "adminId" to adminId,
                    "members" to listOf(adminId), // El admin es el primer miembro
                    "iconResName" to iconResName,
                    "photoUrl" to (photoUrl ?: ""),
                    "createdAt" to System.currentTimeMillis()
                )
            )
            response.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateGroup(
        groupId: String,
        name: String? = null,
        photoUrl: String? = null
    ): Boolean {
        return try {
            val data = mutableMapOf<String, Any>()
            name?.let { data["name"] = it }
            photoUrl?.let { data["photoUrl"] = it }

            databases.updateDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                documentId = groupId,
                data = data
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllGroups(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMyGroupsMemberIds(userId: String): Set<String> {
        return try {
            val myGroups = databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                queries = listOf(
                    Query.equal("members", userId)
                )
            ).documents
            
            val memberIds = mutableSetOf<String>()
            myGroups.forEach { group ->
                val members = group.data["members"] as? List<String>
                members?.let { memberIds.addAll(it) }
            }
            memberIds
        } catch (e: Exception) {
            setOf(userId) // Al menos devolvemos el propio ID
        }
    }

    suspend fun joinGroup(groupId: String, userId: String, currentMembers: List<String>): Boolean {
        return try {
            if (currentMembers.contains(userId)) return true
            
            val newMembers = currentMembers + userId
            databases.updateDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                documentId = groupId,
                data = mapOf("members" to newMembers)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- SECCIÓN DE STORIES (ESTADOS/STAMPS VISUALES) ---

    suspend fun createStory(
        userId: String,
        userName: String,
        userProfilePic: String?,
        imageUrl: String,
        caption: String? = null
    ): Boolean {
        return try {
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STORIES_ID,
                documentId = io.appwrite.ID.unique(),
                data = mapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userProfilePic" to (userProfilePic ?: ""),
                    "imageUrl" to imageUrl,
                    "caption" to (caption ?: ""),
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to System.currentTimeMillis() + (6 * 60 * 60 * 1000) // Expira en 6h
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getActiveStories(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STORIES_ID,
                queries = listOf(
                    Query.greaterThan("expiresAt", System.currentTimeMillis()),
                    Query.orderDesc("createdAt")
                )
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        text: String,
        imageUrl: String? = null,
        fileUrl: String? = null,
        fileName: String? = null
    ): Boolean {
        return try {
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                documentId = io.appwrite.ID.unique(),
                data = mapOf(
                    "groupId" to groupId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis(),
                    "imageUrl" to (imageUrl ?: ""),
                    "fileUrl" to (fileUrl ?: ""),
                    "fileName" to (fileName ?: "")
                )
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("AppwriteChat", "Error al enviar mensaje: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getGroupMessages(groupId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                queries = listOf(
                    Query.equal("groupId", groupId),
                    Query.orderAsc("timestamp"),
                    Query.limit(100)
                )
            ).documents
        } catch (e: Exception) {
            android.util.Log.e("AppwriteChat", "Error al cargar mensajes: ${e.message}")
            emptyList()
        }
    }
}
