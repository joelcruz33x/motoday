package com.example.motoday.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.UserEntity
import com.example.motoday.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mi Moto", "Pasaporte", "Logros", "Estadísticas")

    // Si el usuario no existe, creamos uno por defecto la primera vez solo si la DB está vacía
    LaunchedEffect(Unit) {
        val current = db.userDao().getUserProfile().first()
        if (current == null) {
            db.userDao().insertOrUpdate(
                UserEntity(
                    name = "Nuevo Motero",
                    level = "Principiante",
                    bikeModel = "Tu Moto",
                    bikeSpecs = "Cilindraje",
                    bikeYear = "Año",
                    bikeColor = "Color",
                    profilePictureUri = null,
                    bikePictureUri = null
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil Motero") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(if (isEditing) Icons.Default.Close else Icons.Default.Edit, contentDescription = "Editar")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            userProfile?.let { user ->
                if (isEditing) {
                    EditProfileForm(user) { updatedUser ->
                        scope.launch {
                            db.userDao().insertOrUpdate(updatedUser)
                            isEditing = false
                        }
                    }
                } else {
                    // Header
                    ProfileHeader(user, onImageSelected = { uriString ->
                        try {
                            val uri = Uri.parse(uriString)
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        scope.launch {
                            db.userDao().insertOrUpdate(user.copy(profilePictureUri = uriString))
                        }
                    })

                    // Tabs
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }

                    // Content based on tab
                    when (selectedTab) {
                        0 -> MyBikeSection(user, navController) { uriString ->
                            try {
                                val uri = Uri.parse(uriString)
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            scope.launch {
                                db.userDao().insertOrUpdate(user.copy(bikePictureUri = uriString))
                            }
                        }
                        1 -> PassportSection()
                        2 -> AchievementsSection(user)
                        3 -> StatsSection(user)
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun EditProfileForm(user: UserEntity, onSave: (UserEntity) -> Unit) {
    var name by remember { mutableStateOf(user.name) }
    var bikeModel by remember { mutableStateOf(user.bikeModel) }
    var bikeSpecs by remember { mutableStateOf(user.bikeSpecs) }
    var bikeYear by remember { mutableStateOf(user.bikeYear) }
    var bikeColor by remember { mutableStateOf(user.bikeColor) }

    LazyColumn(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Editar Información Personal", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()) }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { Text("Editar Información de la Moto", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedTextField(value = bikeModel, onValueChange = { bikeModel = it }, label = { Text("Modelo de Moto") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeSpecs, onValueChange = { bikeSpecs = it }, label = { Text("Especificaciones (cc, hp)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeYear, onValueChange = { bikeYear = it }, label = { Text("Año") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = bikeColor, onValueChange = { bikeColor = it }, label = { Text("Color") }, modifier = Modifier.fillMaxWidth()) }
        
        item {
            Button(
                onClick = {
                    onSave(user.copy(
                        name = name,
                        bikeModel = bikeModel,
                        bikeSpecs = bikeSpecs,
                        bikeYear = bikeYear,
                        bikeColor = bikeColor
                    ))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Guardar Cambios")
            }
        }
    }
}

@Composable
fun ProfileHeader(user: UserEntity, onImageSelected: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImageSelected(it.toString()) }
        }
    )

    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (user.profilePictureUri != null) {
                AsyncImage(
                    model = user.profilePictureUri,
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Overlay de edición
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(bottom = 4.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "Nivel: ${user.level}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun MyBikeSection(user: UserEntity, navController: NavController, onImageSelected: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImageSelected(it.toString()) }
        }
    )

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                            .clickable {
                                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (user.bikePictureUri != null) {
                            AsyncImage(
                                model = user.bikePictureUri,
                                contentDescription = "Foto de tu moto",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                                Text("Añadir foto de tu moto", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = user.bikeModel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = "${user.bikeSpecs} | ${user.bikeYear} | ${user.bikeColor}", color = Color.Gray)
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BikeSpecItem("Motor", user.bikeSpecs.take(5))
                        BikeSpecItem("Año", user.bikeYear)
                        BikeSpecItem("Estado", "Excelente")
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(Screen.Maintenance.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hoja de Vida de la Moto")
            }
        }
    }
}

@Composable
fun BikeSpecItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PassportSection() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())
    
    // Agrupamos por ciudad para tener sellos únicos en el pasaporte principal
    val uniqueStamps = stamps.distinctBy { it.locationName.lowercase() }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(text = "Mi Pasaporte de Rutas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = "Colecciona sellos visitando nuevas ciudades", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        if (uniqueStamps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes sellos. ¡Sal a rodar!", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uniqueStamps) { stamp ->
                    CityStamp(stamp)
                }
            }
        }
    }
}

@Composable
fun CityStamp(stamp: com.example.motoday.data.local.entities.PassportStampEntity) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F5))
                .border(2.dp, Color(0xFF6200EE).copy(alpha = 0.3f), CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LocationCity,
                    contentDescription = null,
                    tint = Color(0xFF6200EE).copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stamp.locationName.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6200EE),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(stamp.date)),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun AchievementsSection(user: UserEntity) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text(text = "Mis Trofeos y Medallas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "Cumple objetivos para desbloquear recompensas visuales", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            AchievementItem(
                title = "Bautizo de Asfalto",
                desc = "Completa tu primera rodada",
                isUnlocked = user.ridesCompleted >= 1
            )
        }
        item {
            AchievementItem(
                title = "Explorador Regional",
                desc = "Visita 3 ciudades diferentes",
                isUnlocked = stamps.distinctBy { it.locationName.lowercase() }.size >= 3
            )
        }
        item {
            AchievementItem(
                title = "Tragakilómetros",
                desc = if (user.useMiles) "Suma más de 310 millas totales" else "Suma más de 500km totales",
                isUnlocked = user.totalKilometers >= 500
            )
        }
        item {
            AchievementItem(
                title = "Leyenda de la Carretera",
                desc = "Visita 10 ciudades diferentes",
                isUnlocked = stamps.distinctBy { it.locationName.lowercase() }.size >= 10
            )
        }
    }
}

@Composable
fun StatsSection(user: UserEntity) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stamps by db.passportDao().getAllStamps().collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            Text(text = "Rendimiento Motero", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val unit = if (user.useMiles) "Mi" else "Kms"
                val distance = if (user.useMiles) (user.totalKilometers * 0.621371).toInt() else user.totalKilometers
                Box(modifier = Modifier.weight(1f)) { StatCard(unit, "$distance", Icons.Default.Place, MaterialTheme.colorScheme.primary) }
                Box(modifier = Modifier.weight(1f)) { StatCard("Rutas", "${user.ridesCompleted}", Icons.Default.CheckCircle, Color(0xFF4CAF50)) }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Historial de Sellos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(stamps.size) { index ->
            val stamp = stamps[index]
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00FFFF).copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6200EE).copy(alpha = 0.1f))
            ) {
                ListItem(
                    headlineContent = { 
                        Text(
                            stamp.rideTitle, 
                            style = MaterialTheme.typography.bodyMedium, 
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6200EE)
                        ) 
                    },
                    supportingContent = { Text("${stamp.locationName} - ${sdf.format(Date(stamp.date))}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { 
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Verified, 
                                contentDescription = null, 
                                tint = Color(0xFFFFB100),
                                modifier = Modifier.size(42.dp)
                            )
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF6200EE),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun AchievementItem(title: String, desc: String, isUnlocked: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color(0xFF00FFFF).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f)
        )
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    fontWeight = FontWeight.Bold, 
                    color = if (isUnlocked) Color(0xFF6200EE) else Color.Gray
                ) 
            },
            supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { 
                Icon(
                    if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFFFFB100) else Color.Gray
                )
            }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
