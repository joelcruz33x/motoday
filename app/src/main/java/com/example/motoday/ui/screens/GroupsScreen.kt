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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.ui.components.BottomNavigationBar
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: String,
    val message: String,
    val time: String,
    val isMe: Boolean = false
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
    val allGroups = remember {
        mutableStateListOf(
            GroupInfo(1, "Los Halcones", Icons.Default.Groups, Color(0xFF673AB7)),
            GroupInfo(2, "Ruta 66", Icons.Default.Explore, Color(0xFFE91E63)),
            GroupInfo(3, "Mecánica Pro", Icons.Default.Build, Color(0xFFFF9800)),
            GroupInfo(4, "Eventos", Icons.Default.Event, Color(0xFF4CAF50)),
            GroupInfo(5, "Compra/Venta", Icons.Default.Store, Color(0xFF2196F3))
        )
    }
    
    var selectedGroupId by remember { mutableIntStateOf(1) }
    var isExploring by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val selectedGroup = allGroups.find { it.id == selectedGroupId } ?: allGroups[0]
    
    var messageText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                val newGroup = GroupInfo(allGroups.size + 1, name, Icons.Default.Groups, Color.DarkGray)
                allGroups.add(newGroup)
                selectedGroupId = newGroup.id
                isExploring = false
                showCreateDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditGroupDialog(
            group = selectedGroup,
            onDismiss = { showEditDialog = false },
            onUpdate = { name, uri ->
                val index = allGroups.indexOfFirst { it.id == selectedGroup.id }
                if (index != -1) {
                    allGroups[index] = allGroups[index].copy(name = name, photoUri = uri)
                }
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
                            Text(selectedGroup.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

                allGroups.forEach { group ->
                    GroupIconCircle(
                        group = group,
                        isSelected = !isExploring && selectedGroupId == group.id,
                        onClick = { 
                            selectedGroupId = group.id 
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
                if (isExploring) {
                    ExploreGroupsContent(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onJoin = { groupName ->
                            val newGroup = GroupInfo(allGroups.size + 1, groupName, Icons.Default.Groups, Color.Gray)
                            allGroups.add(newGroup)
                            selectedGroupId = newGroup.id
                            isExploring = false
                        }
                    )
                } else {
                    ChatContent(
                        messages = chatMessages,
                        listState = listState,
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        onSend = {
                            chatMessages.add(ChatMessage("Yo", messageText, "Ahora", true))
                            messageText = ""
                            scope.launch { listState.animateScrollToItem(chatMessages.size - 1) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreGroupsContent(query: String, onQueryChange: (String) -> Unit, onJoin: (String) -> Unit) {
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
        Text("Sugeridos para ti", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        val suggestions = listOf("Moteros CDMX", "Solo Custom", "Aventureros del Sur", "Mecánica Básica")
        suggestions.filter { it.contains(query, ignoreCase = true) }.forEach { name ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text("1.2k miembros", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { onJoin(name) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Unirse", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    listState: LazyListState,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit
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
                IconButton(onClick = { }) { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Grupo") },
        text = {
            Column {
                Text("Dale un nombre a tu comunidad motera.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank()) onCreate(name) }) { Text("Crear") }
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
                Text(text = msg.message, color = textColor)
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
fun EditGroupDialog(group: GroupInfo, onDismiss: () -> Unit, onUpdate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    var photoUri by remember { mutableStateOf(group.photoUri) }
    
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
                        .background(group.color.copy(alpha = 0.2f))
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
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = group.color)
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
fun GroupIconCircle(group: GroupInfo, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (isSelected) RoundedCornerShape(16.dp) else CircleShape)
                .background(if (isSelected) group.color else group.color.copy(alpha = 0.7f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (group.photoUri != null) {
                AsyncImage(
                    model = group.photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(group.icon, contentDescription = null, tint = Color.White)
            }
        }
    }
}
