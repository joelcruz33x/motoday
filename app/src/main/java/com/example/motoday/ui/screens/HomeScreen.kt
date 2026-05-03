package com.example.motoday.ui.screens

import android.content.Intent
import android.util.Log
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.ui.components.BottomNavigationBar
import com.example.motoday.navigation.Screen
import com.example.motoday.viewmodel.NotificationViewModel
import io.appwrite.models.Document
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, notificationViewModel: NotificationViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val db = AppDatabase.getDatabase(context)
    
    val unreadPrivateMessages by notificationViewModel.unreadPrivateMessages.collectAsState()
    val unreadGroupsCount by notificationViewModel.unreadGroupsCount.collectAsState()
    val storeNotifications by notificationViewModel.storeNotifications.collectAsState()

    var posts by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var activeStories by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var myGroupsMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUserStories by remember { mutableStateOf<List<Document<Map<String, Any>>>?>(null) }
    var isRefreshing by remember { mutableStateOf(true) }
    var isUploadingStory by remember { mutableStateOf(false) }
    
    val seenStoryIds by db.seenStoryDao().getSeenStoryIds().collectAsState(initial = emptyList())
    
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    var currentUserId by remember { mutableStateOf("") }

    fun loadData(showLoading: Boolean = true) {
        notificationViewModel.refreshNotifications()
        scope.launch {
            if (showLoading) isRefreshing = true
            try {
                val userId = authManager.getCurrentUserId() ?: ""
                currentUserId = userId

                // Sincronización rápida del perfil para asegurar que la foto aparezca (especialmente tras reinstalar)
                if (userId.isNotEmpty()) {
                    val remoteProfile = appwrite.getUserProfile(userId)
                    if (remoteProfile != null) {
                        val data = remoteProfile.data
                        val profilePicId = data["profilePic"] as? String
                        val profilePicUrl = if (!profilePicId.isNullOrBlank() && profilePicId != "null") {
                            appwrite.getImageUrl(profilePicId, AppwriteManager.BUCKET_PROFILES_ID)
                        } else null

                        val localProfile = db.userDao().getUserProfileOnce()
                        val updatedProfile = (localProfile ?: com.example.motoday.data.local.entities.UserEntity(id = 1)).copy(
                            name = data["name"] as? String ?: "Motero",
                            level = data["level"] as? String ?: "Novato",
                            profilePictureUri = profilePicUrl,
                            octanos = (data["octanos"] as? Number)?.toInt() ?: 0,
                            totalKilometers = (data["totalKm"] as? Number)?.toInt() ?: 0,
                            ridesCompleted = (data["rides"] as? Number)?.toInt() ?: 0
                        )
                        db.userDao().insertOrUpdate(updatedProfile)
                    }
                }

                posts = appwrite.getPosts()
                
                // Obtenemos los IDs de los miembros de mis grupos
                myGroupsMemberIds = appwrite.getMyGroupsMemberIds(currentUserId)
                
                // Limpieza de historias antiguas del usuario actual
                appwrite.cleanupOldStories(currentUserId)
                
                // Obtenemos todas las historias y filtramos por miembros de mis grupos
                val allStories = appwrite.getActiveStories()
                activeStories = allStories.filter { story ->
                    val storyUserId = story.data["userId"] as? String
                    storyUserId != null && (storyUserId == currentUserId || myGroupsMemberIds.contains(storyUserId))
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error loadData: ${e.message}")
            } finally {
                if (showLoading) isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val storyLoader = context.imageLoader
    LaunchedEffect(activeStories) {
        activeStories.forEach { story ->
            val url = story.data["imageUrl"] as? String
            if (!url.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()
                storyLoader.enqueue(request)
            }
        }
    }

    val storyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        if (uri.scheme == "content") {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error persistiendo permiso story: ${e.message}")
                    }
                }
                isUploadingStory = true
                scope.launch {
                    try {
                        uris.forEach { uri ->
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.use { it.readBytes() } ?: throw Exception("Error al leer imagen")
                            
                            val fileName = "story_${currentUserId}_${System.currentTimeMillis()}.jpg"
                            val inputFile = io.appwrite.models.InputFile.fromBytes(bytes, fileName, "image/jpeg")
                            
                            val fileId = appwrite.uploadImage(inputFile, AppwriteManager.BUCKET_STORIES_ID)
                            val imageUrl = appwrite.getImageUrl(fileId, AppwriteManager.BUCKET_STORIES_ID)
                            
                            appwrite.createStory(
                                userId = currentUserId,
                                userName = userProfile?.name ?: "Motero",
                                userProfilePic = userProfile?.profilePictureUri,
                                imageUrl = imageUrl
                            )
                        }
                        Toast.makeText(context, "¡Estados publicados! 🏍️", Toast.LENGTH_SHORT).show()
                        loadData(showLoading = false)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al subir estados: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isUploadingStory = false
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MotoDay", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(48.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = androidx.compose.material.ripple.rememberRipple(bounded = false),
                                onClick = { navController.navigate(Screen.PrivateChatsList.route) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadPrivateMessages > 0) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = (-6).dp, y = 6.dp)
                                    ) { 
                                        Text(unreadPrivateMessages.toString(), fontSize = 10.sp) 
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Email, 
                                contentDescription = "Chats", 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(48.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = androidx.compose.material.ripple.rememberRipple(bounded = false),
                                onClick = { navController.navigate(Screen.Store.route) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (storeNotifications > 0) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White,
                                        modifier = Modifier.offset(x = (-6).dp, y = 6.dp)
                                    ) { 
                                        Text(storeNotifications.toString(), fontSize = 10.sp) 
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart, 
                                contentDescription = "Tienda", 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
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
                text = { Text("Nuevo Post") }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                homeNotifications = unreadPrivateMessages,
                groupsNotifications = unreadGroupsCount
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    item { 
                        StoriesSection(
                            myProfilePic = userProfile?.profilePictureUri,
                            activeStories = activeStories,
                            currentUserId = currentUserId,
                            onAddStoryClick = { storyLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onSeeStoriesClick = { stories -> 
                                selectedUserStories = stories
                            },
                            isUploading = isUploadingStory,
                            seenStoryIds = seenStoryIds
                        ) 
                    }

                    if (posts.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                Text("No hay posts todavía 🏍️", color = Color.Gray)
                            }
                        }
                    }

                    items(posts, key = { it.id }) { post ->
                        val data = post.data
                        @Suppress("UNCHECKED_CAST")
                        val likes = (data["likes"] as? List<String>) ?: emptyList()
                        val timestamp = when (val rawTimestamp = data["timestamp"]) {
                            is Number -> rawTimestamp.toLong()
                            else -> System.currentTimeMillis()
                        }
                        
                        val postUsername = data["userName"] as? String ?: "Motero"
                        val postContent = data["caption"] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val postImageUrls = (data["imageUrl"] as? List<String>) ?: emptyList()

                        PostItem(
                            username = postUsername,
                            userLevel = data["userLevel"] as? String ?: "Novato",
                            userProfilePic = data["profilePic"] as? String,
                            content = postContent,
                            timestamp = timestamp,
                            imageUrls = postImageUrls,
                            likesCount = likes.size,
                            isLiked = likes.contains(currentUserId),
                            onLikeClick = {
                                scope.launch {
                                    appwrite.toggleLike(post.id, currentUserId, likes)
                                    loadData(showLoading = false) 
                                }
                            },
                            onUserClick = {
                                val userId = data["userId"] as? String
                                if (userId != null) {
                                    navController.navigate(Screen.Profile.createRoute(userId))
                                }
                            },
                            onShareClick = {
                                val shareText = buildString {
                                    append("¡Mira lo que compartió $postUsername en MotoDay! 🏍️\n\n")
                                    if (postContent.isNotBlank()) append("\"$postContent\"\n\n")
                                    if (postImageUrls.isNotEmpty()) {
                                        append("Ver foto: ${postImageUrls.first()}")
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir post"))
                            }
                        )
                    }
                }
            }

            // Overlay para el visor de historias (Nivel superior)
            selectedUserStories?.let { stories ->
                StoryViewerScreen(
                    stories = stories,
                    onStoryViewed = { storyId ->
                        scope.launch {
                            db.seenStoryDao().markAsSeen(com.example.motoday.data.local.entities.SeenStoryEntity(storyId))
                        }
                    },
                    onClose = { selectedUserStories = null }
                )
            }
        }
    }
}

@Composable
fun StoriesSection(
    myProfilePic: String?, 
    activeStories: List<Document<Map<String, Any>>>,
    currentUserId: String,
    onAddStoryClick: () -> Unit,
    onSeeStoriesClick: (List<Document<Map<String, Any>>>) -> Unit,
    isUploading: Boolean,
    seenStoryIds: List<String>
) {
    val groupedStories = activeStories.groupBy { it.data["userId"] as String }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { 
                val myStories = groupedStories[currentUserId] ?: emptyList()
                val hasUnseenMyStories = myStories.any { !seenStoryIds.contains(it.id) }
                
                StoryCircle(
                    name = "Tú", 
                    isMe = true, 
                    imageUri = myProfilePic, 
                    hasStories = myStories.isNotEmpty(),
                    hasUnseenStories = hasUnseenMyStories,
                    isUploading = isUploading,
                    onAddClick = onAddStoryClick,
                    onSeeClick = { onSeeStoriesClick(myStories) }
                ) 
            }
            
            groupedStories.filter { it.key != currentUserId }.forEach { (_, stories) ->
                val firstStory = stories.first().data
                val hasUnseenStories = stories.any { !seenStoryIds.contains(it.id) }
                item {
                    StoryCircle(
                        name = firstStory["userName"] as? String ?: "Motero",
                        isMe = false,
                        imageUri = firstStory["userProfilePic"] as? String,
                        hasStories = true,
                        hasUnseenStories = hasUnseenStories,
                        onAddClick = {},
                        onSeeClick = { onSeeStoriesClick(stories) }
                    )
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
    hasUnseenStories: Boolean = false,
    isUploading: Boolean = false,
    onAddClick: () -> Unit,
    onSeeClick: () -> Unit
) {
    // Animación para el borde si tiene historias no vistas (Naranja MotoDay)
    val infiniteTransition = rememberInfiniteTransition(label = "borderTransition")
    val motoDayOrange = Color(0xFFFF9800)
    val motoDayOrangeLight = Color(0xFFFFB74D)

    val borderColor by infiniteTransition.animateColor(
        initialValue = motoDayOrange,
        targetValue = motoDayOrangeLight,
        animationSpec = infiniteRepeatable(
            animation = tween<Color>(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        modifier = Modifier
            .padding(horizontal = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .border(
                        width = if (hasUnseenStories) 3.dp else 1.dp, 
                        color = if (hasUnseenStories) borderColor else if (hasStories) Color.LightGray else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { if (hasStories) onSeeClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                } else if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.fillMaxSize().padding(10.dp), tint = Color.Gray)
                }
            }
            
            if (isMe) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(text = name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
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
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    onUserClick: () -> Unit = {}
) {
    var localIsLiked by remember(isLiked) { mutableStateOf(isLiked) }
    var localLikesCount by remember(likesCount) { androidx.compose.runtime.mutableIntStateOf(likesCount) }

    val scale by animateFloatAsState(
        targetValue = if (localIsLiked) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LikeAnimation"
    )

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onUserClick() }
            ) {
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
                TextButton(onClick = onShareClick) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}
