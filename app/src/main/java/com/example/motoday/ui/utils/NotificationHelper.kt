package com.example.motoday.ui.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.motoday.MainActivity
import com.example.motoday.R

class NotificationHelper(val context: Context) {
    private val maintenanceChannelId = "maintenance_alerts"
    private val achievementChannelId = "achievement_alerts"
    private val passportChannelId = "passport_alerts"
    private val chatChannelId = "chat_alerts"

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal Mantenimiento
            notificationManager.createNotificationChannel(NotificationChannel(
                maintenanceChannelId, "Mantenimiento", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alertas de cambios de aceite y repuestos" })

            // Canal Logros
            notificationManager.createNotificationChannel(NotificationChannel(
                achievementChannelId, "Logros y Trofeos", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notificaciones al desbloquear nuevos hitos" })

            // Canal Pasaporte
            notificationManager.createNotificationChannel(NotificationChannel(
                passportChannelId, "Pasaporte de Rutas", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos sobre nuevos sellos y ciudades" })

            // Canal Chat
            notificationManager.createNotificationChannel(NotificationChannel(
                chatChannelId, "Mensajes de Grupo", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notificaciones de nuevos mensajes en tus grupos" })

            // Canal Solicitudes
            notificationManager.createNotificationChannel(NotificationChannel(
                "request_alerts", "Solicitudes de Ingreso", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avisos cuando alguien quiere unirse a tu grupo" })
        }
    }

    fun showJoinRequestNotification(groupName: String, requesterName: String) {
        sendNotification("request_alerts", 5001, "Nueva solicitud: $groupName", "$requesterName quiere unirse al club", android.R.drawable.ic_menu_add)
    }

    fun showChatNotification(groupName: String, senderName: String, message: String) {
        sendNotification(chatChannelId, 4001, groupName, "$senderName: $message", android.R.drawable.ic_dialog_email) 
    }

    fun showMaintenanceAlert(title: String, message: String) {
        sendNotification(maintenanceChannelId, 1001, title, message, android.R.drawable.ic_dialog_info)
    }

    fun showAchievementUnlocked(title: String, message: String) {
        sendNotification(achievementChannelId, 2001, "¡Logro Desbloqueado! 🏆", "$title: $message", android.R.drawable.star_big_on)
    }

    fun showNewPassportStamp(cityName: String) {
        sendNotification(passportChannelId, 3001, "¡Nuevo Sello! 📍", "Has sellado tu pasaporte en $cityName. ¡Sigue explorando!", android.R.drawable.ic_menu_myplaces)
    }

    private fun sendNotification(channelId: String, notificationId: Int, title: String, message: String, icon: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
