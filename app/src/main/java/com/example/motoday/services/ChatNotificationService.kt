package com.example.motoday.services

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.ui.utils.NotificationHelper
import io.appwrite.services.Realtime
import kotlinx.coroutines.*

class ChatNotificationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var realtimeSubscription: io.appwrite.models.RealtimeSubscription? = null
    private var groupsSubscription: io.appwrite.models.RealtimeSubscription? = null
    private lateinit var appwrite: AppwriteManager
    
    companion object {
        var activeGroupId: String? = null
        private const val CHANNEL_ID = "chat_notifications_v1"
        private const val NOTIFICATION_ID = 888
    }

    override fun onCreate() {
        super.onCreate()
        appwrite = AppwriteManager.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat Motoday")
            .setContentText("Servicio de mensajes activo")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("ChatService", "Error iniciando foreground: ${e.message}")
        }

        startListening()
        return START_STICKY
    }

    private fun startListening() {
        realtimeSubscription?.close()
        groupsSubscription?.close()
        
        serviceScope.launch {
            // Breve retraso para permitir que la App se estabilice y Room termine migraciones si es necesario
            delay(2000)
            
            val auth = AuthManager(applicationContext)
            val notificationHelper = NotificationHelper(applicationContext)
            
            try {
                val userId = auth.getCurrentUserId() ?: return@launch
                val realtime = Realtime(appwrite.client)

                realtimeSubscription = realtime.subscribe(
                    "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_MESSAGES_ID}.documents"
                ) { event ->
                    val payload = event.payload as? Map<String, Any> ?: return@subscribe
                    val senderId = payload["senderId"] as? String
                    val msgGroupId = payload["groupId"] as? String

                    if (senderId != null && senderId != userId && msgGroupId != activeGroupId) {
                        val senderName = payload["senderName"] as? String ?: "Usuario"
                        val text = (payload["text"] as? String ?: "").trim()
                        val rawImageUrl = payload["imageUrl"] as? String
                        
                        val content = when {
                            text.isNotEmpty() && text != "📷 Foto" -> text
                            !rawImageUrl.isNullOrEmpty() -> "📷 Foto"
                            else -> "Nuevo mensaje"
                        }
                        notificationHelper.showChatNotification("Nuevo mensaje", senderName, content)
                    }
                }

                // Suscripción a solicitudes de unión (Escuchando cambios en la colección de grupos)
                groupsSubscription = realtime.subscribe(
                    "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_GROUPS_ID}.documents"
                ) { event ->
                    val payload = event.payload as? Map<String, Any> ?: return@subscribe
                    val adminId = payload["adminId"] as? String
                    
                    // Solo si el usuario actual es el admin del grupo
                    if (adminId == userId) {
                        val lastRequesterId = payload["lastRequesterId"] as? String
                        val lastRequestTime = (payload["lastRequestTime"] as? Number)?.toLong() ?: 0L
                        
                        // Si hay un solicitante reciente (últimos 30 segundos para evitar duplicados en reconexiones)
                        if (lastRequesterId != null && (System.currentTimeMillis() - lastRequestTime) < 30000) {
                            serviceScope.launch {
                                val requesterProfile = appwrite.getUserProfile(lastRequesterId)
                                val requesterName = requesterProfile?.data?.get("name") as? String ?: "Un motero"
                                val groupName = payload["name"] as? String ?: "tu grupo"
                                notificationHelper.showJoinRequestNotification(groupName, requesterName)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatService", "Error en suscripción: ${e.message}")
                delay(10000)
                startListening()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Mensajes en tiempo real", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        realtimeSubscription?.close()
        groupsSubscription?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
