package com.example.motoday.ui.screens

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
import com.example.motoday.ui.components.BottomNavigationBar
import kotlinx.coroutines.launch
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.models.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatMessage(
    val sender: String,
    val message: String,
    val time: String,
    val isMe: Boolean = false,
    val imageUri: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val id: String = UUID.randomUUID().toString()
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
    var showEditDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val selectedGroup = allGroups.find { it.id == selectedGroupId }
    
    var messageText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // Cargar mensajes al seleccionar un grupo
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null) {
            scope.launch {
                val remoteMessages = appwrite.getGroupMessages(selectedGroupId!!)
                chatMessages.clear()
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                remoteMessages.forEach { doc ->
                    val senderId = doc.data["senderId"] as? String ?: ""
                    val ts = doc.data["timestamp"] as? Number ?: 0L
                    chatMessages.add(ChatMessage(
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
    }

    // Cargar datos iniciales
    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        currentUserId = userId
        
        if (userId != null) {
            val profile = appwrite.getUserProfile(userId)
            currentUserName = profile?.data?.get("name") as? String ?: "Motero"
        }

        val remoteGroups = appwrite.getAllGroups()
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
                                    uploadedUrl = appwrite.getImageUrl(fileId)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // 2. Crear el grupo con la URL de la foto
                        val newGroupId = appwrite.createGroup(userId, name, "Comunidad motera", photoUrl = uploadedUrl)
                        
                        if (newGroupId != null) {
                            val remoteGroups = appwrite.getAllGroups()
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

    if (showEditDialog && selectedGroup != null) {
        EditGroupDialog(
            currentName = selectedGroup.data["name"] as? String ?: "",
            currentPhotoUri = null, // Podríamos sacar esto de la data si existiera
            onDismiss = { showEditDialog = false },
            onUpdate = { name, uri ->
                // Aquí se llamaría a appwrite.updateGroup en el futuro
                showEditDialog = false
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
                            DropdownMenuItem(
                                text = { Text("Editar Grupo") },
                                onClick = {
                                    showMenu = false
                                    showEditDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Info. del Grupo") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
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
                    GroupIconCircle(
                        name = doc.data["name"] as? String ?: "Grupo",
                        photoUrl = doc.data["photoUrl"] as? String,
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
                        onJoin = { groupId, members ->
                            scope.launch {
                                val success = appwrite.joinGroup(groupId, currentUserId ?: "", members)
                                if (success) {
                                    val remoteGroups = appwrite.getAllGroups()
                                    allGroups.clear()
                                    allGroups.addAll(remoteGroups)
                                    selectedGroupId = groupId
                                    isExploring = false
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
                                
                                // 1. Crear mensaje temporal
                                val tempMsg = ChatMessage(currentUserName, textToSend, "Enviando...", true)
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
                                        if (success) {
                                            chatMessages[index] = chatMessages[index].copy(time = sdf.format(Date()))
                                        } else {
                                            chatMessages[index] = chatMessages[index].copy(time = "Error")
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
    onJoin: (String, List<String>) -> Unit
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
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text("${members.size} miembros", style = MaterialTheme.typography.labelSmall)
                        }
                        if (!isMember) {
                            Button(
                                onClick = { onJoin(doc.id, members) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Unirse", fontSize = 12.sp)
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
                Text(
                    text = msg.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun EditGroupDialog(
    currentName: String,
    currentPhotoUri: String?,
    onDismiss: () -> Unit,
    onUpdate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var photoUri by remember { mutableStateOf(currentPhotoUri) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Grupo") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = { launcher.launch("image/*") }) {
                    Text("Cambiar foto")
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
            Button(onClick = { if(name.isNotBlank()) onUpdate(name, photoUri) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
