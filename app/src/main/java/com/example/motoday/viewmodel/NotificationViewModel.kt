package com.example.motoday.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.services.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val appwriteManager = AppwriteManager.getInstance(application)
    private val authManager = AuthManager(application)

    private val _unreadPrivateMessages = MutableStateFlow(0)
    val unreadPrivateMessages: StateFlow<Int> = _unreadPrivateMessages.asStateFlow()

    private val _unreadGroupsCount = MutableStateFlow(0)
    val unreadGroupsCount: StateFlow<Int> = _unreadGroupsCount.asStateFlow()

    private val _unreadMessagesPerGroup = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessagesPerGroup: StateFlow<Map<String, Int>> = _unreadMessagesPerGroup.asStateFlow()

    private val _unreadMessagesPerConversation = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessagesPerConversation: StateFlow<Map<String, Int>> = _unreadMessagesPerConversation.asStateFlow()

    private val _profileNotifications = MutableStateFlow(0)
    val profileNotifications: StateFlow<Int> = _profileNotifications.asStateFlow()

    private val _storeNotifications = MutableStateFlow(0)
    val storeNotifications: StateFlow<Int> = _storeNotifications.asStateFlow()

    private var realtimeSubscription: Any? = null
    private var lastUserId: String? = null

    init {
        // Intentar iniciar Realtime al inicio
        startRealtime()
    }

    /**
     * Inicia o reinicia la suscripción a tiempo real si el usuario ha cambiado
     */
    fun startRealtime() {
        viewModelScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            
            if (userId == lastUserId && realtimeSubscription != null) return@launch
            
            lastUserId = userId
            
            // Cerrar suscripción previa si existe
            closeRealtime()

            // Primera carga manual
            refreshNotifications()

            try {
                Log.d("NotificationVM", "Iniciando Realtime para usuario: $userId")
                val realtime = Realtime(appwriteManager.client)
                realtimeSubscription = realtime.subscribe(
                    "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_MESSAGES_ID}.documents"
                ) { event ->
                    val payload = event.payload as? Map<*, *>
                    if (payload != null) {
                        val senderId = payload["senderId"] as? String
                        
                        // Si el mensaje es para nosotros (no enviado por nosotros), refrescamos contadores
                        if (senderId != userId) {
                            Log.d("NotificationVM", "Mensaje entrante detectado vía Realtime")
                            refreshNotifications()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationVM", "Error al configurar Realtime: ${e.message}")
            }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            try {
                // Obtenemos los conteos actualizados desde Appwrite
                val privateCount = appwriteManager.getUnreadPrivateMessagesCount(userId)
                val unreadPerGroup = appwriteManager.getUnreadMessagesPerGroup(userId)
                val unreadPerConversation = appwriteManager.getUnreadMessagesPerConversation(userId)
                val groupsCount = unreadPerGroup.values.sum()
                
                _unreadPrivateMessages.value = privateCount
                _unreadMessagesPerGroup.value = unreadPerGroup
                _unreadMessagesPerConversation.value = unreadPerConversation
                _unreadGroupsCount.value = groupsCount
                
                Log.d("NotificationVM", "Contadores actualizados: Privados=$privateCount, Grupos=$groupsCount")
            } catch (e: Exception) {
                Log.e("NotificationVM", "Error al refrescar notificaciones: ${e.message}")
            }
        }
    }

    fun notifyNewProfileContent() {
        _profileNotifications.value += 1
    }

    fun clearProfileNotifications() {
        _profileNotifications.value = 0
    }

    private fun closeRealtime() {
        try {
            val closeMethod = realtimeSubscription?.javaClass?.getMethod("close")
            closeMethod?.invoke(realtimeSubscription)
            realtimeSubscription = null
        } catch (e: Exception) {
            // Ignorar
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeRealtime()
    }
}
