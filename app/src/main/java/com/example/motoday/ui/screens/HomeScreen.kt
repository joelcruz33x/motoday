package com.example.motoday.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.remote.AppwriteManager
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.ui.components.BottomNavigationBar
import com.example.motoday.navigation.Screen
import io.appwrite.models.Document
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val db = AppDatabase.getDatabase(context)
    
    var posts by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(true) }
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    var currentUserId by remember { mutableStateOf("") }

    fun loadPosts(showLoading: Boolean = true) {
        scope.launch {
            if (showLoading) isRefreshing = true
            try {
                currentUserId = authManager.getCurrentUserId() ?: ""
                posts = appwrite.getPosts()
            } catch (e: Exception) {
                // Error log
            } finally {
                if (showLoading) isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPosts()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MotoDay", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.SOS.route) }) {
                        Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.CreatePost.route) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo Post") } // Corregido: Nuevo Post
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        if (isRefreshing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(padding)
            ) {
                item { StoriesSection(userProfile?.profilePictureUri) }

                if (posts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                            Text("No hay posts todavía 🏍️", color = Color.Gray)
                        }
                    }
                }

                items(posts, key = { it.id }) { post ->
                    val data = post.data
                    val likes = (data["likes"] as? List<String>) ?: emptyList()
                    
                    // Conversión segura de Number (Double o Long) a Long
                    val rawTimestamp = data["timestamp"]
                    val timestamp = when (rawTimestamp) {
                        is Number -> rawTimestamp.toLong()
                        else -> System.currentTimeMillis()
                    }

                    PostItem(
                        username = data["userName"] as? String ?: "Motero",
                        userLevel = data["userLevel"] as? String ?: "Novato",
                        userProfilePic = data["profilePic"] as? String,
                        content = data["caption"] as? String ?: "",
                        timestamp = timestamp,
                        imageUrls = (data["imageUrl"] as? List<String>) ?: emptyList(),
                        likesCount = likes.size,
                        isLiked = likes.contains(currentUserId),
                        onLikeClick = {
                            scope.launch {
                                // Actualización silenciosa (sin pantalla de carga)
                                appwrite.toggleLike(post.id, currentUserId, likes)
                                loadPosts(showLoading = false) 
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PostItem(
    username: String, 
    userLevel: String,
    userProfilePic: String?, 
    content: String, 
    timestamp: Long, 
    imageUrls: List<String>,
    likesCount: Int,
    isLiked: Boolean,
    onLikeClick: () -> Unit
) {
    // Estado local para respuesta inmediata
    var localIsLiked by remember(isLiked) { mutableStateOf(isLiked) }
    var localLikesCount by remember(likesCount) { mutableStateOf(likesCount) }

    // Animación de latido
    val scale by animateFloatAsState(
        targetValue = if (localIsLiked) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LikeAnimation"
    )

    // Cálculo de tiempo real (hace cuánto fue publicado)
    val timeAgo = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.LightGray)) {
                    if (!userProfilePic.isNullOrEmpty()) {
                        AsyncImage(model = userProfilePic, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "• $userLevel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(text = timeAgo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            if (content.isNotBlank()) {
                Text(text = content, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Carrusel de imágenes
            if (imageUrls.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))) {
                    if (imageUrls.size == 1) {
                        AsyncImage(model = imageUrls[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        LazyRow(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(imageUrls) { url ->
                                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxHeight().width(280.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Divider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    // Cambio visual instantáneo
                    localIsLiked = !localIsLiked
                    if (localIsLiked) localLikesCount++ else localLikesCount--
                    onLikeClick()
                }) {
                    Icon(
                        imageVector = if (localIsLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(scale),
                        tint = if (localIsLiked) Color.Red else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("$localLikesCount likes", fontSize = 13.sp, color = if (localIsLiked) Color.Red else Color.Gray)
                }
                TextButton(onClick = { }) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun StoriesSection(myProfilePic: String?) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StoryCircle(name = "Tú", isMe = true, imageUri = myProfilePic, hasStories = false) }
            items(5) { i -> StoryCircle(name = "Motero $i", isMe = false, imageUri = null, hasStories = true) }
        }
    }
}

@Composable
fun StoryCircle(name: String, isMe: Boolean, imageUri: String?, hasStories: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(modifier = Modifier.size(70.dp).border(2.dp, if (hasStories) Color(0xFF6200EE) else Color.LightGray, CircleShape).padding(3.dp).clip(CircleShape).background(Color.White)) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.fillMaxSize().padding(10.dp), tint = Color.Gray)
            }
        }
        Text(text = name, fontSize = 11.sp, maxLines = 1)
    }
}
