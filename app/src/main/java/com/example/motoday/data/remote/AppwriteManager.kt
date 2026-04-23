package com.example.motoday.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.models.Document
import io.appwrite.models.InputFile
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage
import java.util.UUID

class AppwriteManager private constructor(context: Context) {
    val client = Client(context)
        .setEndpoint("https://nyc.cloud.appwrite.io/v1")
        .setProject("69e6836b00267f431c20") 

    val account = Account(client)
    val databases = Databases(client)
    val storage = Storage(client)
    val gson = Gson()

    companion object {
        const val DATABASE_ID = "69e81a9500157d642919"
        const val COLLECTION_PROFILES_ID = "profiles"
        const val COLLECTION_GROUPS_ID = "groups"
        const val COLLECTION_MESSAGES_ID = "messages"
        const val COLLECTION_POSTS_ID = "posts"
        const val COLLECTION_STORIES_ID = "stories"
        const val COLLECTION_STAMPS_ID = "stamps"
        const val BUCKET_PROFILES_ID = "69e844ea000bbe88673c"

        @Volatile
        private var INSTANCE: AppwriteManager? = null

        fun getInstance(context: Context): AppwriteManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppwriteManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- PERFILES ---
    suspend fun getUserProfile(userId: String): Document<Map<String, Any>>? {
        return try {
            databases.getDocument(DATABASE_ID, COLLECTION_PROFILES_ID, userId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserProfile(
        userId: String,
        name: String,
        level: String,
        bikeModel: String,
        bikeSpecs: String,
        bikeYear: String,
        bikeColor: String,
        profilePic: String? = null,
        bikePic: String? = null,
        totalKm: Int? = null,
        rides: Int? = null
    ): Boolean {
        return try {
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "level" to level,
                "bikeModel" to bikeModel,
                "bikeSpecs" to bikeSpecs,
                "bikeYear" to bikeYear,
                "bikeColor" to bikeColor
            )
            if (profilePic != null) data["profilePic"] = profilePic
            if (bikePic != null) data["bikePic"] = bikePic
            if (totalKm != null) data["totalKm"] = totalKm
            if (rides != null) data["rides"] = rides
            
            databases.updateDocument(DATABASE_ID, COLLECTION_PROFILES_ID, userId, data)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUsersProfiles(userIds: List<String>): List<Document<Map<String, Any>>> {
        return try {
            if (userIds.isEmpty()) return emptyList()
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_PROFILES_ID,
                queries = listOf(Query.equal("\$id", userIds))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- SELLOS / PASAPORTE ---
    suspend fun syncStamp(
        userId: String,
        rideId: Int,
        rideTitle: String,
        locationName: String,
        iconResName: String,
        date: Long
    ): Boolean {
        return try {
            val data = mapOf(
                "userId" to userId,
                "rideId" to rideId,
                "rideTitle" to rideTitle,
                "locationName" to locationName,
                "iconResName" to iconResName,
                "date" to date
            )
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STAMPS_ID,
                documentId = ID.unique(),
                data = data
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("AppwriteSync", "Error al sincronizar sello: ${e.message}")
            false
        }
    }

    suspend fun getUserStamps(userId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STAMPS_ID,
                queries = listOf(Query.equal("userId", userId))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- POSTS ---
    suspend fun getPosts(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_POSTS_ID,
                queries = listOf(Query.orderDesc("timestamp"))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createPost(userId: String, userName: String, userLevel: String, profilePic: String?, imageUrls: List<String>, caption: String, timestamp: Long): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_POSTS_ID,
                documentId = UUID.randomUUID().toString(),
                data = mapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userLevel" to userLevel,
                    "profilePic" to (profilePic ?: ""),
                    "imageUrl" to imageUrls, // Nombre usado en HomeScreen
                    "caption" to caption,
                    "timestamp" to timestamp,
                    "likes" to emptyList<String>()
                )
            )
            response.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun toggleLike(postId: String, userId: String, currentLikes: List<String>): Boolean {
        return try {
            val newLikes = currentLikes.toMutableList()
            if (newLikes.contains(userId)) newLikes.remove(userId) else newLikes.add(userId)
            
            databases.updateDocument(DATABASE_ID, COLLECTION_POSTS_ID, postId, mapOf("likes" to newLikes))
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- HISTORIAS ---
    suspend fun getActiveStories(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(DATABASE_ID, COLLECTION_STORIES_ID).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createStory(userId: String, userName: String, userProfilePic: String?, imageUrl: String): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STORIES_ID,
                documentId = UUID.randomUUID().toString(),
                data = mapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userProfilePic" to (userProfilePic ?: ""),
                    "imageUrl" to imageUrl,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            response.id
        } catch (e: Exception) {
            null
        }
    }

    // --- GRUPOS ---
    suspend fun getGroups(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(DATABASE_ID, COLLECTION_GROUPS_ID).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMyGroupsMemberIds(userId: String): Set<String> {
        return try {
            val groups = databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                queries = listOf(Query.contains("members", listOf(userId)))
            ).documents
            
            val memberIds = mutableSetOf<String>()
            groups.forEach { doc ->
                val members = (doc.data["members"] as? List<*>)?.map { it.toString() }
                members?.let { memberIds.addAll(it) }
            }
            memberIds
        } catch (e: Exception) {
            setOf(userId)
        }
    }

    suspend fun createGroup(name: String, description: String, adminId: String, photoUrl: String?, iconResName: String?): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                documentId = UUID.randomUUID().toString(),
                data = mapOf(
                    "name" to name,
                    "description" to description,
                    "adminId" to adminId,
                    "members" to listOf(adminId),
                    "roles" to gson.toJson(mapOf(adminId to "Presidente")),
                    "iconResName" to (iconResName ?: ""),
                    "photoUrl" to (photoUrl ?: ""),
                    "createdAt" to System.currentTimeMillis()
                )
            )
            response.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateGroup(groupId: String, name: String, photoUrl: String?): Boolean {
        return try {
            databases.updateDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GROUPS_ID,
                documentId = groupId,
                data = mutableMapOf<String, Any>("name" to name).apply {
                    if (photoUrl != null) put("photoUrl", photoUrl)
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun joinGroup(groupId: String, userId: String, currentMembers: List<String>): Boolean {
        return try {
            val updatedMembers = currentMembers.toMutableList()
            if (!updatedMembers.contains(userId)) {
                updatedMembers.add(userId)
                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf("members" to updatedMembers))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun leaveGroup(groupId: String, userId: String): Boolean {
        return try {
            val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val members = (doc.data["members"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
            if (members.contains(userId)) {
                members.remove(userId)
                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf("members" to members))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateMemberRole(groupId: String, userId: String, role: String?): Boolean {
        return try {
            val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val rolesJson = doc.data["roles"] as? String ?: "{}"
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val currentRoles: MutableMap<String, String> = gson.fromJson(rolesJson, type) ?: mutableMapOf()
            
            if (role == null) currentRoles.remove(userId) else currentRoles[userId] = role

            databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf("roles" to gson.toJson(currentRoles)))
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- CHAT ---
    suspend fun getGroupMessages(groupId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                queries = listOf(Query.equal("groupId", groupId), Query.orderAsc("timestamp"))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(groupId: String, senderId: String, senderName: String, text: String): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                documentId = UUID.randomUUID().toString(),
                data = mapOf(
                    "groupId" to groupId,
                    "senderId" to senderId,
                    "senderName" to senderName,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            response.id
        } catch (e: Exception) {
            null
        }
    }

    // --- ALMACENAMIENTO ---
    suspend fun uploadImage(file: InputFile): String {
        val response = storage.createFile(BUCKET_PROFILES_ID, UUID.randomUUID().toString(), file)
        return response.id
    }

    fun getImageUrl(fileId: String): String {
        return "https://nyc.cloud.appwrite.io/v1/storage/buckets/$BUCKET_PROFILES_ID/files/$fileId/view?project=69e6836b00267f431c20"
    }
}
