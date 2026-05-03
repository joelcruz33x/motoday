package com.example.motoday.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.ui.components.ChatBubble
import com.example.motoday.ui.components.ChatMessage
import com.example.motoday.ui.components.MessageStatus
import com.example.motoday.ui.components.ChatInput
import com.example.motoday.ui.components.isLocationMessage
import com.example.motoday.viewmodel.NotificationViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Document
import io.appwrite.services.Realtime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    navController: NavController, 
    targetUserId: String,
    notificationViewModel: NotificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    var targetUser by remember { mutableStateOf<Document<Map<String, Any>>?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserName by remember { mutableStateOf("Motero") }
    
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Generar un ID de conversación único y determinista para el 1-a-1
    val conversationId = remember(currentUserId, targetUserId) {
        if (currentUserId != null) {
            val ids = listOf(currentUserId!!, targetUserId).sorted()
            "chat_${ids[0]}_${ids[1]}"
        } else ""
    }

    // 1. Obtener ID del usuario actual y perfil del objetivo
    LaunchedEffect(targetUserId) {
        val uid = authManager.getCurrentUserId()
        currentUserId = uid
        
        if (uid != null) {
            val profile = appwrite.getUserProfile(uid)
            currentUserName = profile?.data?.get("name") as? String ?: "Motero"
        }
        targetUser = appwrite.getUserProfile(targetUserId)
    }

    // 2. Cargar mensajes cuando la conversación esté lista
    LaunchedEffect(conversationId) {
        if (conversationId.isNotEmpty()) {
            try {
                val remoteMessages = appwrite.getMessages(conversationId)
                chatMessages.clear()
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                remoteMessages.forEach { doc ->
                    val senderId = doc.data["senderId"] as? String ?: ""
                    val ts = (doc.data["timestamp"] as? Number)?.toLong() ?: 0L
                    val text = doc.data["text"] as? String ?: ""
                    val isRead = doc.data["read"] as? Boolean ?: false

                    // Extracción de imagen
                    val rawImgVal = doc.data["imageUrl"]
                    val rawImageUrl = if (rawImgVal is List<*>) rawImgVal.firstOrNull()?.toString() else rawImgVal?.toString()
                    val finalImageUrl = if (!rawImageUrl.isNullOrBlank() && 
                        rawImageUrl != "null" && 
                        !rawImageUrl.contains("[") && 
                        rawImageUrl.length > 5) {
                        appwrite.getImageUrl(rawImageUrl, AppwriteManager.BUCKET_GROUPS_ID)
                    } else null

                    val rawFileId = doc.data["fileId"]
                    val fileId = if (rawFileId is List<*>) rawFileId.firstOrNull()?.toString() else rawFileId?.toString()
                    val fileName = doc.data["fileName"] as? String
                    val fileUrl = if (!fileId.isNullOrBlank() && fileId != "null") {
                        appwrite.getFileUrl(fileId, AppwriteManager.BUCKET_GROUPS_ID)
                    } else null

                    val isLocation = isLocationMessage(text)

                    chatMessages.add(ChatMessage(
                        id = doc.id,
                        sender = senderId,
                        message = text,
                        time = sdf.format(Date(ts)),
                        isMe = senderId == currentUserId,
                        imageUri = finalImageUrl,
                        fileUri = fileUrl,
                        fileName = fileName,
                        isLocation = isLocation,
                        status = if (isRead) MessageStatus.READ else MessageStatus.SENT
                    ))
                    
                    // Si el mensaje no es mío y no está leído, marcarlo como leído
                    if (senderId != currentUserId && !isRead) {
                        scope.launch {
                            appwrite.markMessageAsRead(doc.id)
                            notificationViewModel.refreshNotifications()
                        }
                    }
                }
                if (chatMessages.isNotEmpty()) {
                    listState.scrollToItem(chatMessages.size - 1)
                }
            } catch (e: Exception) {
                Log.e("PrivateChat", "Error cargando mensajes: ${e.message}")
            }
        }
    }

    // 2. Suscribirse a Realtime para mensajes nuevos
    DisposableEffect(conversationId) {
        if (conversationId.isEmpty()) return@DisposableEffect onDispose {}

        val realtime = Realtime(appwrite.client)
        val subscription = realtime.subscribe(
            "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_MESSAGES_ID}.documents"
        ) { event ->
            val payload = event.payload as? Map<String, Any>
            if (payload != null) {
                val msgGroupId = payload["groupId"] as? String
                if (msgGroupId == conversationId) {
                    val senderId = payload["senderId"] as? String ?: ""
                    if (senderId != currentUserId) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val ts = (payload["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        
                        val rawImgVal = payload["imageUrl"]
                        val rawImageUrl = if (rawImgVal is List<*>) rawImgVal.firstOrNull()?.toString() else rawImgVal?.toString()
                        
                        val finalImageUrl = if (!rawImageUrl.isNullOrBlank() && 
                            rawImageUrl != "null" && 
                            !rawImageUrl.contains("[") && 
                            rawImageUrl.length > 5) {
                            appwrite.getImageUrl(rawImageUrl, AppwriteManager.BUCKET_GROUPS_ID)
                        } else null

                        val rawFileId = payload["fileId"]
                        val fileId = if (rawFileId is List<*>) rawFileId.firstOrNull()?.toString() else rawFileId?.toString()
                        val fileName = payload["fileName"] as? String
                        val fileUrl = if (!fileId.isNullOrBlank() && fileId != "null") {
                            appwrite.getFileUrl(fileId, AppwriteManager.BUCKET_GROUPS_ID)
                        } else null

                        val text = payload["text"] as? String ?: ""
                        val isRead = payload["read"] as? Boolean ?: false
                        val isLocation = isLocationMessage(text)

                        val msgId = (payload["\$id"] ?: payload["id"])?.toString() ?: UUID.randomUUID().toString()
                        val newMessage = ChatMessage(
                            id = msgId,
                            sender = senderId,
                            message = text,
                            time = sdf.format(Date(ts)),
                            isMe = false,
                            imageUri = finalImageUrl,
                            fileUri = fileUrl,
                            fileName = fileName,
                            isLocation = isLocation,
                            status = if (isRead) MessageStatus.READ else MessageStatus.SENT
                        )
                        
                        scope.launch {
                            val existingIndex = chatMessages.indexOfFirst { it.id == newMessage.id }
                            if (existingIndex != -1) {
                                // Actualizar si el estado cambió (p.ej. de SENT a READ)
                                chatMessages[existingIndex] = newMessage
                            } else {
                                chatMessages.add(newMessage)
                                listState.animateScrollToItem(chatMessages.size - 1)
                                
                                // Marcar como leído si es para nosotros
                                if (!isRead) {
                                    appwrite.markMessageAsRead(msgId)
                                    notificationViewModel.refreshNotifications()
                                }
                            }
                        }
                    } else {
                        // Es un mensaje nuestro, pero Realtime nos avisa si cambió algo (como el estado READ)
                        val isRead = payload["read"] as? Boolean ?: false
                        val msgId = (payload["\$id"] ?: payload["id"])?.toString() ?: ""
                        if (isRead && msgId.isNotEmpty()) {
                            scope.launch {
                                val index = chatMessages.indexOfFirst { it.id == msgId }
                                if (index != -1 && chatMessages[index].status != MessageStatus.READ) {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.READ)
                                }
                            }
                        }
                    }
                }
            }
        }

        onDispose {
            subscription.close()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cid = conversationId
            val uid = currentUserId
            if (cid.isNotEmpty() && uid != null) {
                scope.launch {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val tempMsg = ChatMessage(
                        sender = uid,
                        message = "",
                        time = sdf.format(Date()),
                        isMe = true,
                        imageUri = uri.toString(),
                        status = MessageStatus.SENDING
                    )
                    chatMessages.add(tempMsg)
                    listState.animateScrollToItem(chatMessages.size - 1)

                    // 2. Subir imagen a Appwrite
                    var fileSizeText = ""
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        if (bytes != null) {
                            val fileSizeMB = bytes.size / (1024.0 * 1024.0)
                            fileSizeText = String.format("%.1f MB", fileSizeMB)
                            
                            if (bytes.size > 10 * 1024 * 1024) {
                                Toast.makeText(context, "La imagen ($fileSizeText) supera el límite de 10MB", Toast.LENGTH_LONG).show()
                                val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                                if (index != -1) chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                                return@launch
                            }

                            val fId = appwrite.uploadImage(
                                io.appwrite.models.InputFile.fromBytes(
                                    bytes = bytes,
                                    filename = "pchat_${System.currentTimeMillis()}.jpg",
                                    mimeType = "image/jpeg"
                                ),
                                AppwriteManager.BUCKET_GROUPS_ID
                            )
                            
                            val success = appwrite.sendMessage(
                                conversationId = cid,
                                senderId = uid,
                                senderName = currentUserName,
                                text = "",
                                imageUrl = fId
                            )
                            
                            val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                            if (index != -1) {
                                if (success != null) {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.SENT, id = success)
                                } else {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PrivateChat", "Error subiendo archivo: ${e.message}")
                        val isSizeError = e is AppwriteException && (
                            e.message?.contains("large", true) == true || 
                            e.response?.contains("too_large") == true || 
                            e.code == 400 ||
                            e.message?.contains("not found", true) == true
                        )
                        if (isSizeError) {
                            Toast.makeText(context, "La imagen ($fileSizeText) es demasiado grande o el servidor rechazó la subida", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Error al enviar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                        if (index != -1) chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                    }
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cid = conversationId
            val uid = currentUserId
            if (cid.isNotEmpty() && uid != null) {
                scope.launch {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val originalFileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: "Documento"

                    val tempMsg = ChatMessage(
                        sender = uid,
                        message = "",
                        time = sdf.format(Date()),
                        isMe = true,
                        fileUri = uri.toString(),
                        fileName = originalFileName,
                        status = MessageStatus.SENDING
                    )
                    chatMessages.add(tempMsg)
                    listState.animateScrollToItem(chatMessages.size - 1)

                    // 2. Subir archivo a Appwrite
                    var fileSizeText = ""
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        if (bytes != null) {
                            val fileSizeMB = bytes.size / (1024.0 * 1024.0)
                            fileSizeText = String.format("%.1f MB", fileSizeMB)
                            
                            if (bytes.size > 10 * 1024 * 1024) {
                                Toast.makeText(context, "El archivo ($fileSizeText) supera el límite de 10MB", Toast.LENGTH_LONG).show()
                                val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                                if (index != -1) chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                                return@launch
                            }

                            val fId = appwrite.uploadImage(
                                io.appwrite.models.InputFile.fromBytes(
                                    bytes = bytes,
                                    filename = originalFileName,
                                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                                ),
                                AppwriteManager.BUCKET_GROUPS_ID
                            )
                            
                            val success = appwrite.sendMessage(
                                conversationId = cid,
                                senderId = uid,
                                senderName = currentUserName,
                                text = "",
                                fileId = fId,
                                fileName = originalFileName
                            )
                            
                            val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                            if (index != -1) {
                                if (success != null) {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.SENT, id = success)
                                } else {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PrivateChat", "Error subiendo archivo: ${e.message}")
                        val isSizeError = e is AppwriteException && (
                            e.message?.contains("large", true) == true || 
                            e.response?.contains("too_large") == true || 
                            e.code == 400 ||
                            e.message?.contains("not found", true) == true
                        )
                        if (isSizeError) {
                            Toast.makeText(context, "El archivo ($fileSizeText) supera el límite de tamaño permitido", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Error al enviar archivo: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                        if (index != -1) chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                    }
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendLocationMessage() {
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val mapsLink = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    val cid = conversationId
                    val uid = currentUserId
                    if (cid.isNotEmpty() && uid != null) {
                        scope.launch {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val tempMsg = ChatMessage(
                                sender = uid,
                                message = mapsLink,
                                time = sdf.format(Date()),
                                isMe = true,
                                isLocation = true,
                                status = MessageStatus.SENDING
                            )
                            chatMessages.add(tempMsg)
                            listState.animateScrollToItem(chatMessages.size - 1)

                            val success = appwrite.sendMessage(
                                conversationId = cid,
                                senderId = uid,
                                senderName = currentUserName,
                                text = mapsLink
                            )

                            val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                            if (index != -1) {
                                if (success != null) {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.SENT, id = success)
                                } else {
                                    chatMessages[index] = chatMessages[index].copy(status = MessageStatus.ERROR)
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val profilePicId = targetUser?.data?.get("profilePic") as? String
                        val profilePicUrl = if (!profilePicId.isNullOrBlank()) {
                            appwrite.getImageUrl(profilePicId, AppwriteManager.BUCKET_PROFILES_ID)
                        } else null

                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                                Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = (targetUser?.data?.get("name") as? String) ?: "Cargando...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val level = targetUser?.data?.get("level") as? String
                            if (level != null) {
                                Text(
                                    text = level,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() && currentUserId != null) {
                        val textToSend = messageText
                        messageText = ""
                        
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val isLocation = isLocationMessage(textToSend)

                        // 1. Crear mensaje temporal con estado SENDING
                        val tempMsg = ChatMessage(
                            sender = currentUserId!!,
                            message = textToSend,
                            time = sdf.format(Date()),
                            isMe = true,
                            isLocation = isLocation,
                            status = MessageStatus.SENDING
                        )
                        chatMessages.add(tempMsg)
                        scope.launch { listState.animateScrollToItem(chatMessages.size - 1) }

                        // 2. Enviar a Appwrite
                        scope.launch {
                            val successId = appwrite.sendMessage(
                                conversationId = conversationId,
                                senderId = currentUserId!!,
                                senderName = currentUserName,
                                text = textToSend
                            )
                            
                            // 3. Actualizar estado del mensaje
                            val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                            if (index != -1) {
                                if (successId != null) {
                                    chatMessages[index] = chatMessages[index].copy(
                                        status = MessageStatus.SENT,
                                        id = successId
                                    )
                                } else {
                                    chatMessages[index] = chatMessages[index].copy(
                                        status = MessageStatus.ERROR
                                    )
                                }
                            }
                        }
                    }
                },
                onImageAttach = { imagePicker.launch("image/*") },
                onFileAttach = { filePicker.launch("*/*") },
                onLocationAttach = { sendLocationMessage() }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                        items(chatMessages) { msg ->
                            ChatBubble(msg, showSenderName = false)
                        }
        }
    }
}
