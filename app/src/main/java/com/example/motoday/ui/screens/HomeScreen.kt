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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.ui.utils.MaintenanceChecker
import com.example.motoday.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val posts by db.postDao().getAllPosts().collectAsState(initial = emptyList())

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
            item { StoriesSection() }

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
                    content = post.content,
                    timestamp = post.timestamp,
                    imageUris = post.imageUris
                )
            }
        }
    }
}

@Composable
fun PostItem(username: String, content: String, timestamp: Long, imageUris: String) {
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
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
fun StoriesSection() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(8) { index ->
                StoryCircle(name = if (index == 0) "Tú" else "Motero_$index", isMe = index == 0)
            }
        }
    }
}

@Composable
fun StoryCircle(name: String, isMe: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .border(
                    width = 2.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(if (isMe) Color.LightGray else MaterialTheme.colorScheme.primary),
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isMe) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                }
            }
        }
        Text(text = name, fontSize = 11.sp, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        
        val items = listOf(
            Triple(Screen.Home.route, Icons.Default.Home, "Inicio"),
            Triple(Screen.Explore.route, Icons.Default.Search, "Explorar"),
            Triple(Screen.Groups.route, Icons.Default.Menu, "Grupos"),
            Triple(Screen.Profile.route, Icons.Default.Person, "Perfil")
        )

        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = currentRoute == route,
                onClick = { 
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
