package com.example.motoday.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.navigation.Screen
import com.example.motoday.ui.components.BottomNavigationBar
import io.appwrite.models.Document
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    val localRides by db.rideDao().getAllRides().collectAsState(initial = emptyList())
    
    // Estado para búsqueda y pestañas
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Todas, 1: Mis Rodadas
    var selectedStatusTab by remember { mutableIntStateOf(0) } // 0: Disponibles, 1: Me uní, 2: En curso, 3: Finalizadas
    var currentUserId by remember { mutableStateOf<String?>(null) }

    // Estado para rodadas remotas
    var remoteRides by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Función para cargar rodadas
    fun refreshRides() {
        isLoading = true
        scope.launch {
            try {
                val result = appwrite.getAllRemoteRides()
                Log.d("ExploreScreen", "Rodadas remotas cargadas: ${result.size}")
                remoteRides = result
            } catch (e: Exception) {
                Log.e("ExploreScreen", "Error cargando rodadas: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        currentUserId = authManager.getCurrentUserId()
        refreshRides()
    }

    // Listener Realtime para actualizaciones globales (Sincroniza Room con Appwrite)
    DisposableEffect(Unit) {
        val realtime = io.appwrite.services.Realtime(appwrite.client)
        val subscription = realtime.subscribe(
            "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_RIDES_ID}.documents"
        ) { event ->
            val payload = event.payload as? Map<*, *>
            val remoteId = payload?.get("\$id")?.toString()
            
            if (payload != null && remoteId != null) {
                val remoteStatus = payload["status"]?.toString() ?: ""
                val participantIds = (payload["participantIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
                
                scope.launch {
                    val localRide = db.rideDao().getRideByRemoteId(remoteId)
                    if (localRide != null) {
                        val userId = authManager.getCurrentUserId()
                        val isAttendingNow = if (localRide.creatorId == userId) true 
                                            else (userId?.let { participantIds.contains(it) } ?: localRide.isAttending)
                        
                        val newCount = if (localRide.creatorId != null && !participantIds.contains(localRide.creatorId)) {
                            participantIds.size + 1
                        } else {
                            participantIds.size
                        }

                        if (localRide.status != remoteStatus || localRide.participantsCount != newCount || localRide.isAttending != isAttendingNow) {
                            Log.d("ExploreRealtime", "Actualizando ruta local $remoteId -> Status: $remoteStatus")
                            db.rideDao().updateRide(localRide.copy(
                                status = remoteStatus,
                                participantsCount = newCount,
                                isAttending = isAttendingNow
                            ))
                        }
                    }
                }
            }
        }
        onDispose { subscription.close() }
    }

    // Limpieza automática de rutas finalizadas hace más de 1 hora
    LaunchedEffect(Unit) {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        db.rideDao().cleanupOldRides(oneHourAgo)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Explorar Rodadas") },
                    actions = {
                        IconButton(onClick = { refreshRides() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                        }
                    }
                )
                
                // Barra de búsqueda
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar rodada por nombre...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                // Selector de pestañas
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Todas") },
                        icon = { Icon(Icons.Default.Public, null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Mis Rodadas") },
                        icon = { Icon(Icons.Default.Person, null) }
                    )
                }

                // Filtro de estado mejorado
                ScrollableTabRow(
                    selectedTabIndex = selectedStatusTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {},
                    indicator = {}
                ) {
                    val statuses = listOf("Disponibles", "Me uní", "En curso", "Finalizadas")
                    statuses.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedStatusTab == index,
                            onClick = { selectedStatusTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedStatusTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selectedStatusTab == index) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateRide.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF6200EE)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear Rodada")
            }
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val filteredLocalRides = localRides.filter { ride ->
                val matchesSearch = ride.title.contains(searchQuery, ignoreCase = true)
                val matchesTab = if (selectedTab == 1) ride.creatorId == currentUserId else true
                
                val matchesStatus = when(selectedStatusTab) {
                    0 -> ride.status == "PLANNED" // Disponibles
                    1 -> ride.isAttending && ride.status != "COMPLETED" // Me uní
                    2 -> ride.status == "ONGOING" // En curso
                    3 -> ride.status == "COMPLETED" // Finalizadas
                    else -> true
                }
                
                matchesSearch && matchesTab && matchesStatus
            }

            val filteredRemoteDocs = remoteRides.filter { doc ->
                val title = doc.data["title"]?.toString() ?: ""
                val creatorId = doc.data["creatorId"]?.toString() ?: ""
                val status = doc.data["status"]?.toString() ?: "PLANNED"
                val participantIds = (doc.data["participantIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
                val isAttending = currentUserId != null && participantIds.contains(currentUserId)
                
                val matchesSearch = title.contains(searchQuery, ignoreCase = true)
                val matchesTab = if (selectedTab == 1) creatorId == currentUserId else true
                
                val matchesStatus = when(selectedStatusTab) {
                    0 -> status == "PLANNED"
                    1 -> isAttending && status != "COMPLETED"
                    2 -> status == "ONGOING"
                    3 -> status == "COMPLETED"
                    else -> true
                }
                
                // Evitar duplicados con locales
                val dateLong = (doc.data["date"] as? Number)?.toLong() ?: 0L
                val isNotLocal = localRides.none { local -> 
                    local.title == title && abs(local.date - dateLong) < 1000
                }
                
                matchesSearch && matchesTab && isNotLocal && matchesStatus
            }

            if (filteredLocalRides.isEmpty() && filteredRemoteDocs.isEmpty() && !isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No se encontraron resultados" 
                                   else if (selectedTab == 1) "Aún no has creado rodadas"
                                   else "No hay rodadas programadas", 
                            color = Color.Gray
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Mostrar Rodadas Locales Primero
            items(filteredLocalRides.size) { index ->
                val ride = filteredLocalRides[index]
                val sdf = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.getDefault())
                val dateString = sdf.format(Date(ride.date))

                RideCard(
                    title = ride.title,
                    difficulty = ride.difficulty,
                    terrainType = ride.terrainType,
                    date = dateString,
                    location = "Desde: ${ride.startLocation} hasta ${ride.endLocation}",
                    status = when(ride.status) {
                        "PLANNED" -> if (ride.isAttending) "Asistiré" else "Programada"
                        "ONGOING" -> "En curso"
                        "COMPLETED" -> "Finalizada"
                        else -> ride.status
                    },
                    participantsCount = ride.participantsCount,
                    onClick = {
                        navController.navigate(Screen.RideDetail.createRoute(ride.id))
                    }
                )
            }

            // Mostrar Rodadas Remotas
            items(filteredRemoteDocs.size) { index ->
                val doc = filteredRemoteDocs[index]
                val data = doc.data
                val sdf = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.getDefault())
                val dateLong = (data["date"] as? Number)?.toLong() ?: 0L
                val dateString = sdf.format(Date(dateLong))
                
                val participants = (data["participantIds"] as? List<*>)?.size ?: 0

                RideCard(
                    title = data["title"].toString(),
                    difficulty = data["difficulty"].toString(),
                    terrainType = data["terrainType"].toString(),
                    date = dateString,
                    location = "Desde: ${data["startLocation"]} hasta ${data["endLocation"]}",
                    status = "Disponible",
                    participantsCount = participants,
                    onClick = {
                        scope.launch {
                            try {
                                // 1. Verificar si ya existe por remoteId (doble check)
                                val remoteId = doc.id
                                val existingLocal = db.rideDao().getRideByRemoteId(remoteId)
                                
                                val localId = if (existingLocal == null) {
                                    // 2. Mapear datos remotos a Entidad Local e Insertar
                                    val newRide = com.example.motoday.data.local.entities.RideEntity(
                                        title = data["title"].toString(),
                                        description = data["description"].toString(),
                                        date = dateLong,
                                        startLocation = data["startLocation"].toString(),
                                        endLocation = data["endLocation"].toString(),
                                        startLat = (data["startLat"] as? Number)?.toDouble() ?: 0.0,
                                        startLng = (data["startLng"] as? Number)?.toDouble() ?: 0.0,
                                        endLat = (data["endLat"] as? Number)?.toDouble() ?: 0.0,
                                        endLng = (data["endLng"] as? Number)?.toDouble() ?: 0.0,
                                        meetingPoint = data["meetingPoint"].toString(),
                                        scheduledStops = (data["scheduledStops"] as? List<*>)?.joinToString(", ") ?: "",
                                        difficulty = data["difficulty"].toString(),
                                        terrainType = data["terrainType"].toString(),
                                        creatorName = data["creatorName"].toString(),
                                        isAttending = false,
                                        participantsCount = participants,
                                        isSynced = true,
                                        remoteId = remoteId,
                                        creatorId = data["creatorId"]?.toString()
                                    )
                                    db.rideDao().insertRide(newRide)
                                    // Buscar el ID recién generado
                                    db.rideDao().getRideByRemoteId(remoteId)?.id ?: 0
                                } else {
                                    existingLocal.id
                                }
                                
                                // 3. Navegar inmediatamente
                                if (localId != 0) {
                                    navController.navigate(Screen.RideDetail.createRoute(localId))
                                }
                            } catch (e: Exception) {
                                Log.e("ExploreScreen", "Error al abrir ruta: ${e.message}")
                                Toast.makeText(context, "Error al abrir la ruta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideCard(
    title: String,
    difficulty: String,
    terrainType: String,
    date: String,
    location: String,
    status: String,
    participantsCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Título arriba
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Fila de etiquetas y estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val diffColor = when (difficulty) {
                        "Fácil" -> Color(0xFF4CAF50)
                        "Intermedio" -> Color(0xFFFF9800)
                        "Difícil" -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    // Etiqueta de Nivel (Dificultad)
                    Surface(
                        color = diffColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nivel: $difficulty",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Etiqueta de Terreno (Gris con texto blanco)
                    Surface(
                        color = Color(0xFF757575), // Gris oscuro para contraste
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Landscape, null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Terreno: $terrainType",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Badge(
                    containerColor = when (status) {
                        "Asistiré" -> Color(0xFF4CAF50)
                        "En curso" -> MaterialTheme.colorScheme.primary
                        "Finalizada" -> Color(0xFFD32F2F)
                        "Programada" -> Color.Gray
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Text(
                        text = status,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = date, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = location, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$participantsCount moteros confirmados",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Ver Detalles de la Ruta", fontWeight = FontWeight.Bold)
            }
        }
    }
}
