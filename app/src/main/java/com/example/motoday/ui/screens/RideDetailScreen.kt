package com.example.motoday.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.PassportStampEntity
import com.example.motoday.data.local.entities.RideEntity
import com.example.motoday.data.network.WeatherApiService
import com.example.motoday.data.network.model.WeatherResponse
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.viewmodel.NotificationViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appwrite.services.Realtime
import kotlinx.coroutines.launch
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    navController: NavController, 
    rideId: Int,
    notificationViewModel: NotificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()
    
    var ride by remember { mutableStateOf<RideEntity?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var weatherInfo by remember { mutableStateOf<WeatherResponse?>(null) }
    val weatherApi = remember { WeatherApiService.create() }
    val weatherApiKey = "519ca3ab947f8914a3366ac64cf291fc"
    
    // Suscripción Realtime para actualizaciones de participantes
    val remoteId = ride?.remoteId
    DisposableEffect(remoteId) {
        if (remoteId == null) return@DisposableEffect onDispose {}

        val realtime = Realtime(appwrite.client)
        val subscription = realtime.subscribe(
            "databases.${AppwriteManager.DATABASE_ID}.collections.${AppwriteManager.COLLECTION_RIDES_ID}.documents.$remoteId"
        ) { event ->
            val payload = event.payload as? Map<*, *>
            if (payload != null) {
                val participantIds = (payload["participantIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
                val newCount = participantIds.size
                val remoteStatus = payload["status"]?.toString() ?: ""
                Log.d("Realtime", "Evento recibido: status=$remoteStatus, participants=${participantIds.size}")
                
                scope.launch {
                    val r = db.rideDao().getRideById(rideId)
                    if (r != null) {
                        val isAttendingNow = if (r.creatorId == currentUserId) true 
                                            else (currentUserId?.let { participantIds.contains(it) } ?: r.isAttending)
                        
                        val correctedCount = if (r.creatorId != null && !participantIds.contains(r.creatorId)) {
                            newCount + 1
                        } else {
                            newCount
                        }

                        val updatedStatus = if (remoteStatus.isNotBlank()) remoteStatus else r.status

                        if (r.participantsCount != correctedCount || r.isAttending != isAttendingNow || r.status != updatedStatus) {
                            Log.d("Realtime", "Aplicando cambios a Room: status $updatedStatus")
                            
                            // LÓGICA AUTOMÁTICA PARA ASISTENTES AL FINALIZAR
                            if (updatedStatus == "COMPLETED" && r.status != "COMPLETED" && isAttendingNow) {
                                // EVITAR DOBLE PROCESAMIENTO SI ES EL CREADOR
                                if (r.creatorId != currentUserId) {
                                    scope.launch {
                                        val success = appwrite.processRideCompletion(
                                            context = context,
                                            db = db,
                                            ride = r,
                                            userId = currentUserId ?: ""
                                        )
                                        if (success) {
                                            notificationViewModel.notifyNewProfileContent()
                                        }
                                    }
                                }
                            }

                            db.rideDao().updateRide(r.copy(
                                participantsCount = correctedCount,
                                isAttending = isAttendingNow,
                                status = updatedStatus
                            ))
                        }
                    }
                }
            }
        }
        
        onDispose {
            subscription.close()
        }
    }
    
    LaunchedEffect(rideId) {
        currentUserId = authManager.getCurrentUserId()
        db.rideDao().getRideByIdFlow(rideId).collect { r ->
            if (r != null) {
                ride = r
                if (weatherInfo == null) {
                    scope.launch {
                        try {
                            val response = if (r.startLat != 0.0) {
                                weatherApi.getWeather(r.startLat, r.startLng, weatherApiKey)
                            } else if (r.startLocation.isNotBlank()) {
                                weatherApi.getWeatherByCity(r.startLocation, weatherApiKey)
                            } else null
                            
                            weatherInfo = response
                        } catch (e: Exception) {
                            Log.e("Weather", "Error en API: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    ride?.let { currentRide ->
        val isCreator = currentRide.creatorId == currentUserId

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentRide.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentRide.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = "${currentRide.participantsCount} moteros confirmados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Gestionado por:
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (currentRide.creatorName.contains(" ") || currentRide.creatorName.length > 15) Icons.Default.Person else Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gestionado por: ${currentRide.creatorName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }

                        if (currentRide.status == "COMPLETED") {
                            Surface(
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Flag, "Finalizada", tint = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("RODADA FINALIZADA", fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val diffColor = when (currentRide.difficulty) {
                                "Fácil" -> Color(0xFF4CAF50)
                                "Intermedio" -> Color(0xFFFF9800)
                                "Difícil" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Surface(color = diffColor, shape = RoundedCornerShape(4.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nivel: ${currentRide.difficulty}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(color = Color(0xFF757575), shape = RoundedCornerShape(4.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Landscape, null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Terreno: ${currentRide.terrainType}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // SECCIÓN DE CLIMA
                        Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            if (weatherInfo != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = "https://openweathermap.org/img/wn/${weatherInfo?.weather?.firstOrNull()?.icon}@2x.png",
                                                contentDescription = null,
                                                modifier = Modifier.size(50.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "${weatherInfo?.main?.temp?.toInt()}°C",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = weatherInfo?.weather?.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: "",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                        Text(text = "EN VIVO", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = currentRide.description, style = MaterialTheme.typography.bodyLarge)

                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        DetailRow(Icons.Default.Place, "Inicio: ${currentRide.startLocation}")
                        DetailRow(Icons.Default.LocationOn, "Destino: ${currentRide.endLocation}")
                        
                        val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                        DetailRow(Icons.Default.Event, "Fecha: ${sdf.format(Date(currentRide.date))}")

                        if (currentRide.scheduledStops.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Paradas Programadas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            currentRide.scheduledStops.split(",").forEach { stop ->
                                if (stop.trim().isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stop.trim(), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        if (currentRide.startLat != 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val gmmIntentUri = "google.navigation:q=${currentRide.startLat},${currentRide.startLng}".toUri()
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Navigation, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Navegar al punto de inicio")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (currentRide.status != "COMPLETED") {
                            if (!isCreator) {
                                if (!currentRide.isAttending) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val userId = currentUserId
                                                val remoteId = currentRide.remoteId
                                                
                                                if (userId != null && remoteId != null) {
                                                    val success = appwrite.joinRemoteRide(remoteId, userId)
                                                    if (success) {
                                                        val updatedRide = currentRide.copy(
                                                            isAttending = true,
                                                            participantsCount = currentRide.participantsCount + 1
                                                        )
                                                        db.rideDao().updateRide(updatedRide)
                                                        Toast.makeText(context, "¡Te has unido a la rodada!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Error al unirse (Verifica permisos)", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Error: Datos de sesión o ID remoto nulos", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Add, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("UNIRSE A LA RODADA")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val userId = currentUserId
                                                val remoteId = currentRide.remoteId
                                                
                                                if (userId != null && remoteId != null) {
                                                    val success = appwrite.leaveRemoteRide(remoteId, userId)
                                                    if (success) {
                                                        val updatedRide = currentRide.copy(
                                                            isAttending = false,
                                                            participantsCount = (currentRide.participantsCount - 1).coerceAtLeast(0)
                                                        )
                                                        db.rideDao().updateRide(updatedRide)
                                                        Toast.makeText(context, "Has cancelado tu asistencia", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Close, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CANCELAR ASISTENCIA")
                                    }
                                }
                            } else {
                                // Feedback para el creador
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Eres el organizador",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (currentRide.status != "COMPLETED" && isCreator) {
                    if (currentRide.status == "PLANNED") {
                        Button(
                            onClick = { 
                                scope.launch { 
                                    val success = currentRide.remoteId?.let { 
                                        appwrite.updateRemoteRide(it, mapOf("status" to "ONGOING"))
                                    } ?: true
                                    
                                    if (success) {
                                        db.rideDao().updateRide(currentRide.copy(status = "ONGOING"))
                                    } else {
                                        Toast.makeText(context, "Error al sincronizar inicio", Toast.LENGTH_SHORT).show()
                                    }
                                } 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COMENZAR RUTA")
                        }
                    } else if (currentRide.status == "ONGOING") {
                        Button(
                            onClick = {
                                scope.launch {
                                    // 1. Marcar como completada en la nube
                                    val success = currentRide.remoteId?.let { 
                                        appwrite.updateRemoteRide(it, mapOf("status" to "COMPLETED"))
                                    } ?: true

                                    if (!success) {
                                        Toast.makeText(context, "Error al sincronizar fin", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // 2. EL CREADOR ACTUALIZA SUS PROPIAS ESTADÍSTICAS INMEDIATAMENTE
                                        val stampSuccess = appwrite.processRideCompletion(
                                            context = context,
                                            db = db,
                                            ride = currentRide,
                                            userId = currentUserId ?: ""
                                        )

                                        if (stampSuccess) {
                                            // 3. Actualizar localmente la ruta
                                            db.rideDao().updateRide(currentRide.copy(status = "COMPLETED"))
                                            notificationViewModel.notifyNewProfileContent()
                                            Toast.makeText(context, "¡Ruta Finalizada y Sello Creado!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Ruta finalizada (Error al crear sello)", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        kotlinx.coroutines.delay(300) 
                                        navController.popBackStack()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("FINALIZAR RUTA")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Función obsoleta, ahora se usa appwrite.processRideCompletion para centralizar la lógica.
 */
@Deprecated("Use appwrite.processRideCompletion")
suspend fun updateUserStatsAndStamp(
    context: android.content.Context,
    db: com.example.motoday.data.local.AppDatabase,
    appwrite: com.example.motoday.data.remote.AppwriteManager,
    authManager: com.example.motoday.data.remote.AuthManager,
    ride: com.example.motoday.data.local.entities.RideEntity,
    currentUserId: String?
) {
    currentUserId?.let { appwrite.processRideCompletion(context, db, ride, it) }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
