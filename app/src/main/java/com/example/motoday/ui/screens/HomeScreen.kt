package com.example.motoday.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.motoday.data.local.entities.StoryEntity
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.ui.components.BottomNavigationBar
import com.example.motoday.ui.utils.MaintenanceChecker
import com.example.motoday.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val posts by db.postDao().getAllPosts().collectAsState(initial = emptyList())
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)

    // REVISAR MANTENIMIENTO al abrir la app
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        MaintenanceChecker(context).checkStatus()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MotoDay", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.SOS.route) }) {
                        Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.CreatePost.route) },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF6200EE)) },
                text = { Text("Publicar", color = Color(0xFF6200EE), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF6200EE)
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(padding)
        ) {
            item { StoriesSection(userProfile?.profilePictureUri) }

            if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Aún no hay publicaciones", color = Color.Gray)
                        Text("¡Sé el primero en compartir algo!", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            items(posts) { post ->
                PostItem(
                    username = post.username,
                    userProfilePic = post.userProfilePic,
                    content = post.content,
                    timestamp = post.timestamp,
                    imageUris = post.imageUris
                )
            }
        }
    }
}

@Composable
fun PostItem(username: String, userProfilePic: String?, content: String, timestamp: Long, imageUris: String) {
    val timeAgo = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    val images = remember(imageUris) {
        imageUris.split(",").filter { it.isNotBlank() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: User Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfilePic != null) {
                        AsyncImage(
                            model = userProfilePic,
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
                    Text(text = username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(text = timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray) }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content: Text
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Content: Images
            if (images.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    if (images.size == 1) {
                        AsyncImage(
                            model = images[0],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(images) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(260.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Footer: Interaction Buttons
            Divider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    TextButton(onClick = { }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PostIcon(Icons.Outlined.FavoriteBorder, contentDescription = null, size = 18.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Me gusta", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PostIcon(Icons.Outlined.Share, contentDescription = null, size = 18.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartir", fontSize = 12.sp)
                        }
                    }
                }
                
                // Comentarios placeholder
                Text("0 comentarios", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

// Helper para iconos con tamaño manual
@Composable
fun PostIcon(imageVector: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size)
    )
}

@Composable
fun StoriesSection(myProfilePic: String?) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val allStories by db.storyDao().getActiveStories(System.currentTimeMillis()).collectAsState(initial = emptyList())
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    // Identificador del usuario actual
    val myCurrentName = userProfile?.name ?: "Motero"
    
    // Agrupar historias por usuario, unificando "Motero" con tu nombre actual
    val groupedStories = remember(allStories, myCurrentName) {
        allStories.groupBy { 
            if (it.username == "Motero") myCurrentName else it.username 
        }
    }
    
    var selectedUserStories by remember { mutableStateOf<List<StoryEntity>?>(null) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            uris.forEach { uri ->
                scope.launch {
                    val newStory = StoryEntity(
                        username = myCurrentName,
                        userProfilePic = userProfile?.profilePictureUri,
                        imageUri = uri.toString()
                    )
                    db.storyDao().insertStory(newStory)
                }
            }
        }
    )

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Círculo para "Tú" - Agrupa todas tus historias sin importar cambios de perfil
            item {
                val myStories = groupedStories[myCurrentName] ?: emptyList()
                StoryCircle(
                    name = "Tú",
                    isMe = true,
                    // Prioridad: 1. Foto de perfil, 2. Imagen de la historia, 3. Null (icono person)
                    imageUri = myProfilePic ?: myStories.firstOrNull()?.imageUri,
                    hasStories = myStories.isNotEmpty(),
                    onClick = {
                        if (myStories.isNotEmpty()) {
                            selectedUserStories = myStories
                        } else {
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    onAddClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }

            // Historias de otros moteros (excluyendo la burbuja de "Tú")
            items(groupedStories.filterKeys { it != myCurrentName }.toList()) { (username, userStories) ->
                StoryCircle(
                    name = username,
                    isMe = false,
                    imageUri = userStories.first().imageUri,
                    hasStories = true,
                    onClick = { selectedUserStories = userStories }
                )
            }
        }
    }

    // Visor de Historias Avanzado
    selectedUserStories?.let { stories ->
        StoryViewer(
            stories = stories,
            onDismiss = { selectedUserStories = null }
        )
    }
}

@Composable
fun StoryViewer(stories: List<StoryEntity>, onDismiss: () -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val storyDuration = 5000L
    
    LaunchedEffect(currentIndex) {
        progress = 0f
        val startTime = System.currentTimeMillis()
        while (progress < 1f) {
            val elapsedTime = System.currentTimeMillis() - startTime
            progress = elapsedTime.toFloat() / storyDuration
            kotlinx.coroutines.delay(16)
        }
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // 1. Imagen de la historia (Fondo)
            AsyncImage(
                model = stories[currentIndex].imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // 2. Zonas de toque para navegación (Debajo de la UI de control)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { 
                    if (currentIndex > 0) currentIndex-- else onDismiss() 
                })
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { 
                    if (currentIndex < stories.size - 1) currentIndex++ else onDismiss() 
                })
            }

            // 3. UI de Control (Encima de todo para que la 'X' sea clickable)
            Column {
                // Barras de progreso
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { index, _ ->
                        LinearProgressIndicator(
                            progress = when {
                                index < currentIndex -> 1f
                                index == currentIndex -> progress
                                else -> 0f
                            },
                            modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                // Cabecera con info y BOTÓN CERRAR
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp, start = 16.dp, end = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)) {
                        AsyncImage(model = stories[currentIndex].userProfilePic, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stories[currentIndex].username, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StoryCircle(
    name: String, 
    isMe: Boolean, 
    imageUri: String?, 
    hasStories: Boolean,
    onClick: () -> Unit,
    onAddClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center
        ) {
            // Círculo principal con imagen
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 2.dp,
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = if (hasStories) listOf(Color(0xFF00FFFF), Color(0xFF6200EE))
                                     else listOf(Color.LightGray, Color.Gray)
                        ),
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Botón de añadir fuera de la burbuja (estilo Instagram)
            if (isMe) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 2.dp, end = 2.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { onAddClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
        Text(
            text = name, 
            fontSize = 11.sp, 
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal, 
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}
