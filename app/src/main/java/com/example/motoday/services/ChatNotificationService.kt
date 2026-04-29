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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startListening()
        return START_STICKY
    }

    private fun startListening() {
        realtimeSubscription?.close()
        serviceScope.launch {
            val auth = AuthManager(applicationContext)
            val userId = auth.getCurrentUserId() ?: return@launch
            val notificationHelper = NotificationHelper(applicationContext)
            val realtime = Realtime(appwrite.client)

            try {
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
            } catch (e: Exception) {
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
