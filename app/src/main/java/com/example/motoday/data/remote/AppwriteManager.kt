package com.example.motoday.data.remote

import android.content.Context
import android.util.Log
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

class AppwriteManager(context: Context) {
    val client = Client(context)
        .setEndpoint("https://nyc.cloud.appwrite.io/v1")
        .setProject("69e6836b00267f431c20") 

    val account = Account(client)
    val databases = Databases(client)
    val storage = Storage(client)
    val gson = Gson()

    companion object {
        // IDs Reales confirmados por Joel
        const val DATABASE_ID = "69e81a9500157d642919"
        const val COLLECTION_USERS_ID = "profiles"
        const val COLLECTION_POSTS_ID = "posts"
        const val COLLECTION_RIDES_ID = "rides"
        const val COLLECTION_GROUPS_ID = "groups"
        const val COLLECTION_STORIES_ID = "stories"
        const val COLLECTION_CONTACTS_ID = "contacts"
        const val COLLECTION_MAINTENANCE_ID = "maintenance"
        const val COLLECTION_GARAGE_ID = "motos"
        const val COLLECTION_BIKE_PHOTOS_ID = "bike_photos"
        
        const val BUCKET_PROFILES_ID = "69e844ea000bbe88673c"
        const val BUCKET_BIKES_ID = "69e844ea000bbe88673c" 
        const val BUCKET_POSTS_ID = "69e844ea000bbe88673c"
        const val BUCKET_STORIES_ID = "69e844ea000bbe88673c"
        const val BUCKET_GROUPS_ID = "69e844ea000bbe88673c"

        // Para compatibilidad con el código existente que usaba estas constantes:
        const val COLLECTION_PROFILES_ID = COLLECTION_USERS_ID 
        const val COLLECTION_MESSAGES_ID = "messages" // No proporcionado, mantenemos el anterior o lo buscamos
        const val COLLECTION_STAMPS_ID = "stamps"     // No proporcionado

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
            databases.getDocument(DATABASE_ID, COLLECTION_USERS_ID, userId)
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error getUserProfile: ${e.message}")
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
        rides: Int? = null,
        isIndependent: Boolean? = null
    ): Boolean {
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
        if (isIndependent != null) data["isIndependent"] = isIndependent

        return try {
            databases.updateDocument(DATABASE_ID, COLLECTION_USERS_ID, userId, data)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error updateUserProfile (Atributos: ${data.keys}): ${e.message}")
            if (e is io.appwrite.exceptions.AppwriteException) {
                Log.e("AppwriteManager", "Código: ${e.code}, Respuesta: ${e.response}")
            }
            false
        }
    }

    suspend fun getUsersProfiles(userIds: List<String>): List<Document<Map<String, Any>>> {
        return try {
            if (userIds.isEmpty()) return emptyList()
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_USERS_ID,
                queries = listOf(Query.equal("\$id", userIds))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- SELLOS / PASAPORTE ---
    suspend fun syncStamp(userId: String, stamp: com.example.motoday.data.local.entities.PassportStampEntity): Boolean {
        return syncStamp(
            userId = userId,
            rideId = stamp.rideId,
            rideTitle = stamp.rideTitle,
            locationName = stamp.locationName,
            iconResName = stamp.iconResName,
            date = stamp.date
        )
    }

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
            Log.e("AppwriteSync", "Error al sincronizar sello: ${e.message}")
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
            val response = databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_POSTS_ID,
                queries = listOf(Query.orderDesc("timestamp"))
            )
            Log.d("AppwriteManager", "Posts recuperados: ${response.documents.size}")
            response.documents
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error getPosts en colección $COLLECTION_POSTS_ID: ${e.message}")
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
                    "imageUrl" to imageUrls, 
                    "caption" to caption,
                    "timestamp" to timestamp,
                    "likes" to emptyList<String>()
                )
            )
            response.id
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error createPost: ${e.message}")
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
            val sixHoursAgo = System.currentTimeMillis() - (6 * 60 * 60 * 1000)
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STORIES_ID,
                queries = listOf(Query.greaterThan("timestamp", sixHoursAgo))
            ).documents
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error getActiveStories: ${e.message}")
            emptyList()
        }
    }

    suspend fun cleanupOldStories(userId: String): Int {
        return try {
            val sixHoursAgo = System.currentTimeMillis() - (6 * 60 * 60 * 1000)
            val oldStories = databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_STORIES_ID,
                queries = listOf(
                    Query.equal("userId", userId),
                    Query.lessThan("timestamp", sixHoursAgo)
                )
            ).documents

            var deletedCount = 0
            oldStories.forEach { doc ->
                val storyId = doc.id
                val imageUrl = doc.data["imageUrl"] as? String
                val fileId = extractFileIdFromUrl(imageUrl)

                // Borrar documento
                databases.deleteDocument(DATABASE_ID, COLLECTION_STORIES_ID, storyId)
                
                // Borrar archivo del storage si existe
                if (fileId != null) {
                    deleteFile(fileId, BUCKET_STORIES_ID)
                }
                deletedCount++
            }
            deletedCount
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error cleanupOldStories: ${e.message}")
            0
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

    suspend fun getGroup(groupId: String): Document<Map<String, Any>>? {
        return try {
            databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
        } catch (e: Exception) {
            null
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

    suspend fun deleteGroup(groupId: String): Boolean {
        return try {
            // 1. Obtener info del grupo para borrar su foto
            val group = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val photoId = group.data["photoUrl"] as? String
            
            // 2. Borrar foto si existe
            if (!photoId.isNullOrBlank()) {
                deleteFile(photoId, BUCKET_GROUPS_ID)
            }

            // 3. Borrar mensajes del chat del grupo
            val messages = databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                queries = listOf(Query.equal("groupId", groupId))
            ).documents
            
            messages.forEach { msg ->
                try {
                    databases.deleteDocument(DATABASE_ID, COLLECTION_MESSAGES_ID, msg.id)
                } catch (e: Exception) {
                    Log.e("AppwriteManager", "Error borrando mensaje ${msg.id}: ${e.message}")
                }
            }

            // 4. Borrar el documento del grupo
            databases.deleteDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error deleteGroup: ${e.message}")
            false
        }
    }

    suspend fun joinGroup(groupId: String, userId: String, currentMembers: List<String>): Boolean {
        return try {
            val updatedMembers = currentMembers.toMutableList()
            if (!updatedMembers.contains(userId)) {
                updatedMembers.add(userId)
                
                // Al unirse, asignar rol de Prospecto por defecto
                val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
                val rolesJson = doc.data["roles"] as? String ?: "{}"
                val type = object : TypeToken<MutableMap<String, String>>() {}.type
                val currentRoles: MutableMap<String, String> = gson.fromJson(rolesJson, type) ?: mutableMapOf()
                currentRoles[userId] = "Prospecto"
                
                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf(
                    "members" to updatedMembers,
                    "roles" to gson.toJson(currentRoles)
                ))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestJoinGroup(groupId: String, userId: String): Boolean {
        return try {
            val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val requests = (doc.data["requests"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
            if (!requests.contains(userId)) {
                requests.add(userId)
                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf("requests" to requests))
            }
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error requestJoinGroup: ${e.message}")
            false
        }
    }

    suspend fun approveJoinRequest(groupId: String, userId: String): Boolean {
        return try {
            val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val requests = (doc.data["requests"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
            val members = (doc.data["members"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
            
            if (requests.contains(userId)) {
                requests.remove(userId)
                if (!members.contains(userId)) {
                    members.add(userId)
                }
                
                val rolesJson = doc.data["roles"] as? String ?: "{}"
                val type = object : TypeToken<MutableMap<String, String>>() {}.type
                val currentRoles: MutableMap<String, String> = gson.fromJson(rolesJson, type) ?: mutableMapOf()
                currentRoles[userId] = "Prospecto"

                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf(
                    "requests" to requests,
                    "members" to members,
                    "roles" to gson.toJson(currentRoles)
                ))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun rejectJoinRequest(groupId: String, userId: String): Boolean {
        return try {
            val doc = databases.getDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId)
            val requests = (doc.data["requests"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
            
            if (requests.contains(userId)) {
                requests.remove(userId)
                databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, mapOf("requests" to requests))
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
            
            val updateData = mutableMapOf<String, Any>()

            if (role == null) {
                currentRoles.remove(userId)
            } else {
                // Si el rol es único (no "Miembro"), quitárselo a quien lo tenga
                if (role != "Miembro") {
                    val previousOwner = currentRoles.filterValues { it == role }.keys.firstOrNull()
                    if (previousOwner != null && previousOwner != userId) {
                        currentRoles[previousOwner] = "Miembro"
                    }
                }
                currentRoles[userId] = role
                
                // Si se asigna el rol de Presidente, transferir también la administración del grupo
                if (role == "Presidente") {
                    updateData["adminId"] = userId
                }
            }

            updateData["roles"] = gson.toJson(currentRoles)
            databases.updateDocument(DATABASE_ID, COLLECTION_GROUPS_ID, groupId, updateData)
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
                queries = listOf(Query.equal("groupId", groupId), Query.orderAsc("timestamp"), Query.limit(100))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(groupId: String, senderId: String, senderName: String, text: String, imageUrl: String? = null): String? {
        return try {
            val data = mutableMapOf(
                "groupId" to groupId,
                "senderId" to senderId,
                "senderName" to senderName,
                "text" to text,
                "timestamp" to System.currentTimeMillis()
            )
            if (imageUrl != null) data["imageUrl"] = imageUrl

            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MESSAGES_ID,
                documentId = UUID.randomUUID().toString(),
                data = data
            )
            response.id
        } catch (e: Exception) {
            null
        }
    }

    // --- CONTACTOS DE EMERGENCIA ---
    suspend fun syncContact(userId: String, name: String, phone: String, relation: String): Boolean {
        return try {
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_CONTACTS_ID,
                documentId = ID.unique(),
                data = mapOf(
                    "userId" to userId,
                    "name" to name,
                    "phoneNumber" to phone,
                    "relationship" to relation
                )
            )
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error syncContact: ${e.message}")
            false
        }
    }

    suspend fun getUserContacts(userId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_CONTACTS_ID,
                queries = listOf(Query.equal("userId", userId))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteRemoteContact(documentId: String): Boolean {
        return try {
            databases.deleteDocument(DATABASE_ID, COLLECTION_CONTACTS_ID, documentId)
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- HOJA DE VIDA (MANTENIMIENTO) ---
    suspend fun syncMaintenance(userId: String, bikeId: String?, type: String, mileage: Int, description: String, cost: Double, date: Long): Boolean {
        return try {
            databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MAINTENANCE_ID,
                documentId = ID.unique(),
                data = mutableMapOf(
                    "userId" to userId,
                    "type" to type,
                    "mileage" to mileage,
                    "description" to description,
                    "cost" to cost,
                    "date" to date
                ).apply {
                    if (bikeId != null) put("bikeId", bikeId)
                }
            )
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error syncMaintenance: ${e.message}")
            false
        }
    }

    suspend fun getUserMaintenanceLogs(userId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_MAINTENANCE_ID,
                queries = listOf(Query.equal("userId", userId), Query.orderDesc("date"))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- GARAJE (MOTOS) ---
    suspend fun syncBike(userId: String, model: String, year: String, color: String, specs: String, status: String, currentKm: Int, picId: String?): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GARAGE_ID,
                documentId = ID.unique(),
                data = mapOf(
                    "userId" to userId,
                    "model" to model,
                    "year" to year,
                    "color" to color,
                    "specs" to specs,
                    "status" to status,
                    "currentKm" to currentKm,
                    "bikePic" to (picId ?: "")
                )
            )
            response.id
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error syncBike: ${e.message}")
            null
        }
    }

    suspend fun updateRemoteBike(bikeId: String, model: String, year: String, color: String, specs: String, status: String, currentKm: Int, picId: String?): Boolean {
        return try {
            val data = mutableMapOf<String, Any>(
                "model" to model,
                "year" to year,
                "color" to color,
                "specs" to specs,
                "status" to status,
                "currentKm" to currentKm
            )
            if (picId != null) data["bikePic"] = picId
            databases.updateDocument(DATABASE_ID, COLLECTION_GARAGE_ID, bikeId, data)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error updateRemoteBike: ${e.message}")
            false
        }
    }

    suspend fun deleteRemoteBike(bikeId: String): Boolean {
        return try {
            databases.deleteDocument(DATABASE_ID, COLLECTION_GARAGE_ID, bikeId)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error deleteRemoteBike: ${e.message}")
            false
        }
    }

    suspend fun getUserBikes(userId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_GARAGE_ID,
                queries = listOf(Query.equal("userId", userId))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- FOTOS DE MOTOS ---
    suspend fun syncBikePhoto(bikeRemoteId: String, fileId: String): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_BIKE_PHOTOS_ID,
                documentId = ID.unique(),
                data = mapOf(
                    "bikeId" to bikeRemoteId,
                    "fileId" to fileId
                )
            )
            response.id
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error syncBikePhoto: ${e.message}")
            null
        }
    }

    suspend fun getBikePhotos(bikeRemoteId: String): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_BIKE_PHOTOS_ID,
                queries = listOf(Query.equal("bikeId", bikeRemoteId))
            ).documents
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- RIDES ---
    suspend fun getAllRemoteRides(): List<Document<Map<String, Any>>> {
        return try {
            databases.listDocuments(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_RIDES_ID,
                queries = listOf(io.appwrite.Query.orderAsc("date"))
            ).documents
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error getAllRemoteRides: ${e.message}")
            emptyList()
        }
    }

    suspend fun createRemoteRide(data: Map<String, Any>): String? {
        return try {
            val response = databases.createDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_RIDES_ID,
                documentId = io.appwrite.ID.unique(),
                data = data
            )
            response.id
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error createRemoteRide: ${e.message}")
            null
        }
    }

    suspend fun updateRemoteRide(rideId: String, data: Map<String, Any>): Boolean {
        return try {
            databases.updateDocument(
                databaseId = DATABASE_ID,
                collectionId = COLLECTION_RIDES_ID,
                documentId = rideId,
                data = data
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteBikePhotoDocument(documentId: String): Boolean {
        return try {
            databases.deleteDocument(DATABASE_ID, COLLECTION_BIKE_PHOTOS_ID, documentId)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error deleteBikePhotoDocument: ${e.message}")
            false
        }
    }

    // --- ALMACENAMIENTO ---
    suspend fun uploadImage(file: InputFile, bucketId: String = BUCKET_POSTS_ID): String {
        val response = storage.createFile(bucketId, ID.unique(), file)
        return response.id
    }

    suspend fun deleteFile(fileId: String, bucketId: String = BUCKET_BIKES_ID): Boolean {
        return try {
            storage.deleteFile(bucketId, fileId)
            true
        } catch (e: Exception) {
            Log.e("AppwriteManager", "Error deleteFile: ${e.message}")
            false
        }
    }

    fun getImageUrl(fileId: String, bucketId: String = BUCKET_POSTS_ID): String {
        if (fileId.isBlank() || fileId == "null") return ""
        // Si ya es una URL completa, no la construimos
        if (fileId.startsWith("http")) return fileId
        
        // Optimizamos la carga añadiendo parámetros de previsualización (view) de Appwrite
        // width=1080 para calidad HD pero optimizada para móvil, quality=80 para ahorrar ancho de banda
        return "https://nyc.cloud.appwrite.io/v1/storage/buckets/$bucketId/files/$fileId/view?project=69e6836b00267f431c20&width=1080&quality=80"
    }

    fun extractFileIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (!url.contains("/files/")) return null
        return try {
            url.substringAfter("/files/").substringBefore("/")
        } catch (e: Exception) {
            null
        }
    }
}
