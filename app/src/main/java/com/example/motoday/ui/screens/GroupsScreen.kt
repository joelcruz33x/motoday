package com.example.motoday.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.navigation.Screen
import com.example.motoday.ui.components.BottomNavigationBar
import kotlinx.coroutines.launch
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.appwrite.models.Document
import io.appwrite.services.Realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MessageStatus {
    SENDING, SENT, READ, ERROR
}

data class ChatMessage(
    val sender: String,
    val message: String,
    val time: String,
    val isMe: Boolean = false,
    val imageUri: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val status: MessageStatus = MessageStatus.SENT
)

data class GroupInfo(
    val id: Int,
    var name: String,
    val icon: ImageVector,
    val color: Color,
    var photoUri: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(navController: NavController) {
    val context = LocalContext.current
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    val allGroups = remember { mutableStateListOf<Document<Map<String, Any>>>() }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var currentUserName by remember { mutableStateOf("Motero") }
    var isLoading by remember { mutableStateOf(true) }
    
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var isExploring by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showRequestsDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val selectedGroup = allGroups.find { it.id == selectedGroupId }
    val gson = remember { Gson() }

    fun getRolesMap(groupDoc: Document<Map<String, Any>>?): Map<String, String> {
        val rolesJson = groupDoc?.data?.get("roles") as? String ?: "{}"
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(rolesJson, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    var messageText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // 1. Cargar mensajes iniciales al seleccionar un grupo
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null) {
            val groupId = selectedGroupId!!
            val remoteMessages = appwrite.getGroupMessages(groupId)
            chatMessages.clear()
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            remoteMessages.forEach { doc ->
                val senderId = doc.data["senderId"] as? String ?: ""
                val ts = doc.data["timestamp"] as? Number ?: 0L
                chatMessages.add(ChatMessage(
                    id = doc.id,
                    sender = doc.data["senderName"] as? String ?: "Usuario",
                    message = doc.data["text"] as? String ?: "",
                    time = sdf.format(Date(ts.toLong())),
                    isMe = senderId == currentUserId
                ))
            }
            if (chatMessages.isNotEmpty()) {
                listState.scrollToItem(chatMessages.size - 1)
            }
        }
    }

    // 2. Suscribirse a Realtime para nuevos mensajes
    DisposableEffect(selectedGroupId) {
        val groupId = selectedGroupId
        if (groupId == null) return@DisposableEffect onDispose {}

        val realtime = Realtime(appwrite.client)
        val subscription = realtime.subscribe(
            "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_MESSAGES_ID}.documents"
        ) { event ->
            val payload = event.payload as? Map<String, Any>
            if (payload != null) {
                val msgGroupId = payload["groupId"] as? String
                if (msgGroupId == groupId) {
                    val senderId = payload["senderId"] as? String ?: ""
                    // Evitar duplicados si somos nosotros quienes enviamos (ya lo añadimos localmente)
                    if (senderId != currentUserId) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val ts = (payload["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        
                        val newMessage = ChatMessage(
                            id = payload["\$id"] as? String ?: UUID.randomUUID().toString(),
                            sender = payload["senderName"] as? String ?: "Usuario",
                            message = payload["text"] as? String ?: "",
                            time = sdf.format(Date(ts)),
                            isMe = false
                        )
                        
                        scope.launch {
                            // Verificar que no esté ya en la lista (por si acaso)
                            if (chatMessages.none { it.id == newMessage.id }) {
                                chatMessages.add(newMessage)
                                listState.animateScrollToItem(chatMessages.size - 1)
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

    // Cargar datos iniciales
    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        currentUserId = userId
        
        if (userId != null) {
            val profile = appwrite.getUserProfile(userId)
            currentUserName = profile?.data?.get("name") as? String ?: "Motero"
        }

        val remoteGroups = appwrite.getGroups()
        allGroups.clear()
        allGroups.addAll(remoteGroups)
        
        // Si el usuario ya está en algún grupo, seleccionar el primero por defecto
        val myFirstGroup = remoteGroups.find { doc ->
            val members = doc.data["members"] as? List<*>
            members?.contains(currentUserId) == true
        }
        if (myFirstGroup != null) {
            selectedGroupId = myFirstGroup.id
            isExploring = false
        }
        isLoading = false
    }

    // Launchers para adjuntos
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            chatMessages.add(ChatMessage("Yo", "", "Ahora", true, imageUri = uri.toString()))
            scope.launch { listState.animateScrollToItem(chatMessages.size - 1) }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            chatMessages.add(ChatMessage("Yo", "", "Ahora", true, fileUri = uri.toString(), fileName = "Documento.pdf"))
            scope.launch { listState.animateScrollToItem(chatMessages.size - 1) }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, imageUri ->
                scope.launch {
                    val userId = currentUserId
                    if (userId != null) {
                        var uploadedUrl: String? = null
                        
                        // 1. Subir imagen si existe
                        if (imageUri != null) {
                            try {
                                val inputStream = context.contentResolver.openInputStream(android.net.Uri.parse(imageUri))
                                val bytes = inputStream?.readBytes()
                                if (bytes != null) {
                                    val fileId = appwrite.uploadImage(
                                        io.appwrite.models.InputFile.fromBytes(
                                            bytes = bytes,
                                            filename = "group_$name.jpg",
                                            mimeType = "image/jpeg"
                                        )
                                    )
                                    uploadedUrl = fileId // Guardamos solo el ID para que quepa en la DB
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // 2. Crear el grupo con la lógica correcta de parámetros
                        val newGroupId = appwrite.createGroup(
                            name = name,
                            description = "Comunidad motera",
                            adminId = userId,
                            photoUrl = uploadedUrl,
                            iconResName = null
                        )
                        
                        if (newGroupId != null) {
                            val remoteGroups = appwrite.getGroups()
                            allGroups.clear()
                            allGroups.addAll(remoteGroups)
                            selectedGroupId = newGroupId
                            isExploring = false
                        }
                    }
                    showCreateDialog = false
                }
            }
        )
    }

    if (showMembersDialog && selectedGroup != null) {
        val memberIds = (selectedGroup.data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val rolesMap = getRolesMap(selectedGroup)
        val adminId = selectedGroup.data["adminId"] as? String ?: ""
        
        GroupMembersDialog(
            memberIds = memberIds,
            adminId = adminId,
            roles = rolesMap,
            appwrite = appwrite,
            onDismiss = { showMembersDialog = false }
        )
    }

    if (showRequestsDialog && selectedGroup != null) {
        val requests = (selectedGroup.data["requests"] as? List<*>)?.map { it.toString() } ?: emptyList()
        GroupRequestsDialog(
            groupId = selectedGroupId!!,
            requestUserIds = requests,
            appwrite = appwrite,
            onDismiss = { showRequestsDialog = false },
            onAction = {
                scope.launch {
                    val remoteGroups = appwrite.getGroups()
                    allGroups.clear()
                    allGroups.addAll(remoteGroups)
                    showRequestsDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isExploring) {
                        Text("Explorar Grupos", fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            val name = selectedGroup?.data?.get("name") as? String ?: "Selecciona Grupo"
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Comunidad activa", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    if (!isExploring) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            val isAdmin = selectedGroup?.data?.get("adminId") == currentUserId
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Solicitudes de Ingreso") },
                                    onClick = {
                                        showMenu = false
                                        showRequestsDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ajustes del Grupo") },
                                    onClick = {
                                        showMenu = false
                                        navController.navigate(Screen.GroupSettings.createRoute(selectedGroupId!!))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ver Miembros") },
                                onClick = {
                                    showMenu = false
                                    showMembersDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.People, contentDescription = null) }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Abandonar Grupo", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    selectedGroupId?.let { gid ->
                                        scope.launch {
                                            val success = appwrite.leaveGroup(gid, currentUserId ?: "")
                                            if (success) {
                                                val remoteGroups = appwrite.getGroups()
                                                allGroups.clear()
                                                allGroups.addAll(remoteGroups)
                                                selectedGroupId = null
                                                isExploring = true
                                            }
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isExploring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { isExploring = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = "Explorar", 
                        tint = if (isExploring) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

                allGroups.filter { doc ->
                    val members = doc.data["members"] as? List<*>
                    members?.contains(currentUserId) == true
                }.forEach { doc ->
                    val rawPhotoUrl = doc.data["photoUrl"] as? String
                    val finalPhotoUrl = if (!rawPhotoUrl.isNullOrEmpty()) appwrite.getImageUrl(rawPhotoUrl, AppwriteManager.BUCKET_GROUPS_ID) else null
                    
                    GroupIconCircle(
                        name = doc.data["name"] as? String ?: "Grupo",
                        photoUrl = finalPhotoUrl,
                        isSelected = !isExploring && selectedGroupId == doc.id,
                        onClick = { 
                            selectedGroupId = doc.id 
                            isExploring = false
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { showCreateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear Grupo", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (isExploring) {
                    ExploreGroupsContent(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        allGroups = allGroups,
                        currentUserId = currentUserId ?: "",
                        appwrite = appwrite, 
                        onJoin = { groupId, members ->
                            scope.launch {
                                val success = appwrite.requestJoinGroup(groupId, currentUserId ?: "")
                                if (success) {
                                    Toast.makeText(context, "Solicitud enviada al club", Toast.LENGTH_SHORT).show()
                                    val remoteGroups = appwrite.getGroups()
                                    allGroups.clear()
                                    allGroups.addAll(remoteGroups)
                                }
                            }
                        }
                    )
                } else {
                    ChatContent(
                        groupName = selectedGroup?.data?.get("name") as? String ?: "Chat",
                        messages = chatMessages,
                        listState = listState,
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSend = {
                            val textToSend = messageText
                            val groupId = selectedGroupId
                            val userId = currentUserId
                            
                            if (groupId != null && userId != null && textToSend.isNotBlank()) {
                                messageText = ""
                                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                
                                // 1. Crear mensaje temporal con estado SENDING
                                val tempMsg = ChatMessage(
                                    sender = currentUserName,
                                    message = textToSend,
                                    time = sdf.format(Date()),
                                    isMe = true,
                                    status = MessageStatus.SENDING
                                )
                                chatMessages.add(tempMsg)
                                scope.launch { listState.animateScrollToItem(chatMessages.size - 1) }

                                // 2. Enviar a Appwrite
                                scope.launch {
                                    val success = appwrite.sendMessage(
                                        groupId = groupId,
                                        senderId = userId,
                                        senderName = currentUserName,
                                        text = textToSend
                                    )
                                    
                                    // 3. Actualizar estado del mensaje
                                    val index = chatMessages.indexOfFirst { it.id == tempMsg.id }
                                    if (index != -1) {
                                        if (success != null) {
                                            chatMessages[index] = chatMessages[index].copy(
                                                status = MessageStatus.SENT,
                                                time = sdf.format(Date())
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
                        onFileAttach = { filePicker.launch("*/*") }
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreGroupsContent(
    query: String,
    onQueryChange: (String) -> Unit,
    allGroups: List<Document<Map<String, Any>>>,
    currentUserId: String,
    onJoin: (String, List<String>) -> Unit,
    appwrite: AppwriteManager // Añadimos el manager para obtener URLs
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar grupos (ej: Enduro, Bogotá...)") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Explorar Grupos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        val filteredGroups = allGroups.filter { 
            (it.data["name"] as? String)?.contains(query, ignoreCase = true) == true 
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredGroups) { doc ->
                val name = doc.data["name"] as? String ?: "Grupo"
                val photoId = doc.data["photoUrl"] as? String
                val photoUrl = if (!photoId.isNullOrEmpty()) appwrite.getImageUrl(photoId, AppwriteManager.BUCKET_GROUPS_ID) else null
                val members = (doc.data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val isMember = members.contains(currentUserId)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text("${members.size} miembros", style = MaterialTheme.typography.labelSmall)
                        }
                        if (!isMember) {
                    val requests = (doc.data["requests"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    val hasRequested = requests.contains(currentUserId)

                    Button(
                        onClick = { if (!hasRequested) onJoin(doc.id, members) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        enabled = !hasRequested,
                        colors = if (hasRequested) ButtonDefaults.buttonColors(containerColor = Color.Gray) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (hasRequested) "Pendiente" else "Unirse", fontSize = 12.sp)
                    }
                } else {
                            Text("Miembro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatContent(
    groupName: String,
    messages: List<ChatMessage>,
    listState: LazyListState,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onImageAttach: () -> Unit,
    onFileAttach: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg -> ChatBubble(msg) }
        }
        
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(8.dp).navigationBarsPadding().imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showAttachMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showAttachMenu = true }) { 
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Adjuntar", tint = MaterialTheme.colorScheme.primary) 
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Imagen") },
                            onClick = {
                                showAttachMenu = false
                                onImageAttach()
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Documento") },
                            onClick = {
                                showAttachMenu = false
                                onFileAttach()
                            },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                        )
                    }
                }
                
                TextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    placeholder = { Text("Escribe un mensaje...") },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    maxLines = 4
                )
                if (messageText.isNotBlank()) {
                    IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Grupo") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = { launcher.launch("image/*") }) {
                    Text("Seleccionar foto")
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank()) onCreate(name, imageUri) }) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (!msg.isMe) {
            Text(
                text = msg.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isMe) 16.dp else 4.dp,
                bottomEnd = if (msg.isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.imageUri != null) {
                    AsyncImage(
                        model = msg.imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (msg.fileUri != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = textColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg.fileName ?: "Archivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (msg.message.isNotBlank()) {
                    Text(text = msg.message, color = textColor)
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = msg.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    
                    if (msg.isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val (icon, tint) = when (msg.status) {
                            MessageStatus.SENDING -> Icons.Default.Schedule to textColor.copy(alpha = 0.5f)
                            MessageStatus.SENT -> Icons.Default.Check to textColor.copy(alpha = 0.7f)
                            MessageStatus.READ -> Icons.Default.DoneAll to Color(0xFF00BFFF)
                            MessageStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupMembersDialog(
    memberIds: List<String>,
    adminId: String,
    roles: Map<String, String>,
    appwrite: AppwriteManager,
    onDismiss: () -> Unit
) {
    var membersProfiles by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val gson = remember { Gson() }

    LaunchedEffect(memberIds) {
        membersProfiles = appwrite.getUsersProfiles(memberIds)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Miembros del Grupo") },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(membersProfiles) { profile ->
                            val userId = profile.id
                            val name = profile.data["name"] as? String ?: "Motero"
                            val photoUrl = profile.data["profilePic"] as? String
                            val level = profile.data["level"] as? String ?: "Novato"
                            val userRole = roles[userId]

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!photoUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = appwrite.getImageUrl(photoUrl, AppwriteManager.BUCKET_PROFILES_ID),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = name, 
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        
                                        if (userId == adminId) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Stars, 
                                                contentDescription = "Admin", 
                                                tint = Color(0xFFFFB100), 
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (userRole != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val badgeColor = when (userRole) {
                                                "Presidente" -> Color(0xFFFFD700)
                                                "Vicepresidente" -> Color(0xFFC0C0C0)
                                                "Sargento de Armas" -> Color(0xFFD32F2F)
                                                "Secretario" -> Color(0xFF2196F3)
                                                "Tesorero" -> Color(0xFF4CAF50)
                                                "Capitán de Ruta" -> Color(0xFFFF9800)
                                                "Prospecto" -> Color(0xFF9E9E9E)
                                                else -> MaterialTheme.colorScheme.secondary
                                            }
                                            
                                            Surface(
                                                color = badgeColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = userRole.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = badgeColor
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = level, 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}


@Composable
fun GroupRequestsDialog(
    groupId: String,
    requestUserIds: List<String>,
    appwrite: AppwriteManager,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    var profiles by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestUserIds) {
        profiles = appwrite.getUsersProfiles(requestUserIds)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitudes de Ingreso") },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (profiles.isEmpty()) {
                    Text("No hay solicitudes pendientes", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(profiles) { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = appwrite.getImageUrl(profile.data["profilePic"] as? String ?: "", AppwriteManager.BUCKET_PROFILES_ID),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = remember { androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.data["name"] as? String ?: "Motero", fontWeight = FontWeight.Bold)
                                    Text(profile.data["level"] as? String ?: "Novato", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (appwrite.approveJoinRequest(groupId, profile.id)) {
                                            onAction()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Aprobar", tint = Color(0xFF4CAF50))
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        if (appwrite.rejectJoinRequest(groupId, profile.id)) {
                                            onAction()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun GroupIconCircle(name: String, photoUrl: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador lateral izquierdo (la barrita)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (isSelected) 32.dp else 8.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // El icono o foto del grupo
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (isSelected) RoundedCornerShape(12.dp) else CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
