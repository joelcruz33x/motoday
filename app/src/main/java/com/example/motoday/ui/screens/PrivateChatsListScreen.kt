package com.example.motoday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.navigation.Screen
import com.example.motoday.viewmodel.NotificationViewModel
import io.appwrite.Query
import io.appwrite.models.Document
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatsListScreen(
    navController: NavController,
    notificationViewModel: NotificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()
    
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var chatSummaries by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val unreadMessagesPerConversation by notificationViewModel.unreadMessagesPerConversation.collectAsState()

    LaunchedEffect(Unit) {
        notificationViewModel.refreshNotifications()
        val uid = authManager.getCurrentUserId()
        currentUserId = uid
        
        if (uid != null) {
            try {
                // Buscamos todos los mensajes donde el usuario es remitente o el grupo contiene su ID
                // Pero como usamos conversationId "chat_ID1_ID2", buscaremos mensajes que empiecen con "chat_"
                // y contengan el userId. Appwrite Query.contains podría no ser suficiente si no indexamos.
                // Una alternativa es buscar por senderId = uid O por el prefijo del groupId.
                
                // Estrategia: Obtener mensajes recientes del usuario
                val response = appwrite.databases.listDocuments(
                    databaseId = AppwriteManager.DATABASE_ID,
                    collectionId = AppwriteManager.COLLECTION_MESSAGES_ID,
                    queries = listOf(
                        Query.orderDesc("timestamp"),
                        Query.limit(100)
                    )
                )

                val allMessages = response.documents
                val myPrivateMessages = allMessages.filter { doc ->
                    val gid = doc.data["groupId"] as? String ?: ""
                    gid.startsWith("chat_") && gid.contains(uid)
                }

                // Agrupar por conversationId para tener el último mensaje de cada chat
                val latestMessages = myPrivateMessages.groupBy { it.data["groupId"] as String }
                    .mapValues { entry -> entry.value.first() }

                val summaries = mutableListOf<ChatSummary>()
                
                latestMessages.forEach { (convId, lastMsg) ->
                    val otherUserId = convId.replace("chat_", "").replace(uid, "").replace("_", "")
                    val otherProfile = appwrite.getUserProfile(otherUserId)
                    
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val ts = (lastMsg.data["timestamp"] as? Number)?.toLong() ?: 0L
                    
                    summaries.add(ChatSummary(
                        targetUserId = otherUserId,
                        targetUserName = otherProfile?.data?.get("name") as? String ?: "Motero",
                        targetUserProfilePic = otherProfile?.data?.get("profilePic") as? String,
                        lastMessage = lastMsg.data["text"] as? String ?: "",
                        time = sdf.format(Date(ts)),
                        timestamp = ts,
                        conversationId = convId
                    ))
                }
                
                chatSummaries = summaries.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensajes Privados") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (chatSummaries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tienes mensajes privados aún", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(chatSummaries) { chat ->
                    val unreadCount = unreadMessagesPerConversation[chat.conversationId] ?: 0
                    ChatListItem(chat, unreadCount) {
                        navController.navigate(Screen.PrivateChat.createRoute(chat.targetUserId))
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatSummary, unreadCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val appwrite = AppwriteManager.getInstance(LocalContext.current)
        val profilePicUrl = if (!chat.targetUserProfilePic.isNullOrBlank()) {
            appwrite.getImageUrl(chat.targetUserProfilePic, AppwriteManager.BUCKET_PROFILES_ID)
        } else null

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (profilePicUrl != null) {
                AsyncImage(
                    model = profilePicUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.targetUserName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = chat.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unreadCount > 0) Color(0xFF00FFFF) else Color.Gray,
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .background(Color(0xFF00FFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color(0xFF6200EE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

data class ChatSummary(
    val targetUserId: String,
    val targetUserName: String,
    val targetUserProfilePic: String?,
    val lastMessage: String,
    val time: String,
    val timestamp: Long,
    val conversationId: String
)
