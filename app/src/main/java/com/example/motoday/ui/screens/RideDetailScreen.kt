package com.example.motoday.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.motoday.data.local.entities.UserEntity
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import kotlin.math.*
import com.example.motoday.data.network.WeatherApiService
import com.example.motoday.data.network.model.WeatherResponse
import com.example.motoday.ui.utils.NotificationHelper
import com.example.motoday.ui.utils.MaintenanceChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.location.Geocoder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(navController: NavController, rideId: Int) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }
    
    var ride by remember { mutableStateOf<RideEntity?>(null) }
    
    LaunchedEffect(rideId) {
        db.rideDao().getRideByIdFlow(rideId).collect { ride = it }
    }

    ride?.let { currentRide ->
        val statusText = when (currentRide.status) {
            "PLANNED" -> "Programada"
            "ONGOING" -> "En curso"
            "COMPLETED" -> "Finalizada"
            else -> currentRide.status
        }

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
                // Estado de la ruta en el top
                Text(
                    text = "Estado de ruta: $statusText",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Tarjeta de Dificultad
                                CategoryCard(
                                    label = "Dificultad",
                                    value = currentRide.difficulty,
                                    icon = Icons.Default.TrendingUp
                                )
                                // Tarjeta de Terreno
                                CategoryCard(
                                    label = "Tipo de Terreno",
                                    value = currentRide.terrainType,
                                    icon = Icons.Default.Landscape
                                )
                            }
                            
                            // Botón de Asistir (Tarjeta interactiva)
                            Card(
                                onClick = {
                                    scope.launch {
                                        val updatedRide = currentRide.copy(
                                            isAttending = !currentRide.isAttending,
                                            participantsCount = if (!currentRide.isAttending) currentRide.participantsCount + 1 else currentRide.participantsCount - 1
                                        )
                                        db.rideDao().updateRide(updatedRide)
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentRide.isAttending) Color(0xFF4CAF50) else Color(0xFFE0E0E0)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (currentRide.isAttending) "Asistiré" else "Asistir",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (currentRide.isAttending) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${currentRide.participantsCount} moteros confirmados",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = currentRide.description, style = MaterialTheme.typography.bodyLarge)
                        
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        DetailRow(Icons.Default.Place, "Inicio: ${currentRide.startLocation}")
                        DetailRow(Icons.Default.LocationOn, "Destino: ${currentRide.endLocation}")
                        DetailRow(Icons.Default.MeetingRoom, "Encuentro: ${currentRide.meetingPoint}")

                        // Weather Section
                        if (currentRide.endLat != 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            WeatherSection(lat = currentRide.endLat, lon = currentRide.endLng)
                        }

                        if (currentRide.startLat != 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=${currentRide.startLat},${currentRide.startLng}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Navegar al punto de inicio")
                            }
                        }
                        
                        if (currentRide.scheduledStops.isNotBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "ITINERARIO DE LA RUTA",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val stops = currentRide.scheduledStops.split(",").map { it.trim() }
                            stops.forEachIndexed { index, stop ->
                                StopItem(index + 1, stop, isLast = index == stops.size - 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controles de Ruta
                if (currentRide.status != "COMPLETED") {
                    Text(text = "Control de viaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (currentRide.status == "PLANNED") {
                        Button(
                            onClick = { scope.launch { db.rideDao().updateRide(currentRide.copy(status = "ONGOING")) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("COMENZAR RUTA")
                        }
                    } else if (currentRide.status == "ONGOING") {
                        Button(
                            onClick = {
                                scope.launch {
                                    // 1. Calcular distancia (Haversine formula para kms entre coordenadas)
                                    val distanceKm = if (currentRide.startLat != 0.0 && currentRide.endLat != 0.0) {
                                        calculateDistance(currentRide.startLat, currentRide.startLng, currentRide.endLat, currentRide.endLng).toInt()
                                    } else 50 // Valor por defecto si no hay coordenadas

                                    // 2. Finalizar ruta
                                    db.rideDao().updateRide(currentRide.copy(status = "COMPLETED"))
                                    
                                    // 3. Actualizar perfil de usuario (kms y rutas completadas)
                                    val user = db.userDao().getUserProfile().firstOrNull()
                                    user?.let {
                                        val newKms = it.totalKilometers + distanceKm
                                        val newRides = it.ridesCompleted + 1
                                        
                                        // Comprobar logros para notificaciones
                                        val notifier = NotificationHelper(context)
                                        if (newRides == 1) notifier.showAchievementUnlocked("Bautizo de Asfalto", "¡Has completado tu primera rodada!")
                                        if (it.totalKilometers < 500 && newKms >= 500) notifier.showAchievementUnlocked("Tragakilómetros", "¡Has superado los 500km totales!")
                                        
                                        db.userDao().insertOrUpdate(it.copy(
                                            ridesCompleted = newRides,
                                            totalKilometers = newKms
                                        ))

                                        // Sincronizar estadísticas con Appwrite
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val userId = authManager.getCurrentUserId()
                                                if (userId != null) {
                                                    appwrite.updateUserProfile(
                                                        userId = userId,
                                                        name = it.name,
                                                        level = it.level,
                                                        bikeModel = it.bikeModel,
                                                        bikeSpecs = it.bikeSpecs,
                                                        bikeYear = it.bikeYear,
                                                        bikeColor = it.bikeColor,
                                                        totalKm = newKms,
                                                        rides = newRides
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("RideDetail", "Error sync stats: ${e.message}")
                                            }
                                        }
                                    }

                                    // 4. Añadir sello al pasaporte
                                    val stamps = db.passportDao().getAllStamps().firstOrNull() ?: emptyList()
                                    val citiesVisited = stamps.distinctBy { it.locationName.lowercase() }.size
                                    
                                    // Intentar detectar la ciudad real por coordenadas si el nombre es genérico
                                    var detectedCity = currentRide.endLocation
                                    if (detectedCity.contains("Ubicación", ignoreCase = true) && currentRide.endLat != 0.0) {
                                        try {
                                            val geocoder = Geocoder(context, Locale.getDefault())
                                            val addresses = withContext(Dispatchers.IO) {
                                                geocoder.getFromLocation(currentRide.endLat, currentRide.endLng, 1)
                                            }
                                            val city = addresses?.firstOrNull()?.locality ?: addresses?.firstOrNull()?.subAdminArea
                                            if (city != null) detectedCity = city
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    val endLocationClean = detectedCity.trim().lowercase()
                                    val iconName = when {
                                        endLocationClean.contains("machala") -> "ic_stamp_machala"
                                        endLocationClean.contains("guayaquil") -> "ic_stamp_guayaquil"
                                        endLocationClean.contains("cuenca") -> "ic_stamp_cuenca"
                                        endLocationClean.contains("quito") -> "ic_stamp_quito"
                                        else -> "ic_stamp_default"
                                    }

                                    db.passportDao().insertStamp(
                                        PassportStampEntity(
                                            rideId = currentRide.id,
                                            rideTitle = currentRide.title,
                                            date = System.currentTimeMillis(),
                                            locationName = detectedCity,
                                            iconResName = iconName
                                        )
                                    )

                                    // 4.1 Sincronizar con Appwrite (Nube)
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val userId = authManager.getCurrentUserId()
                                            if (userId != null) {
                                                appwrite.syncStamp(
                                                    userId = userId,
                                                    rideId = currentRide.id,
                                                    rideTitle = currentRide.title,
                                                    locationName = detectedCity,
                                                    iconResName = iconName,
                                                    date = System.currentTimeMillis()
                                                )
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    
                                    // Notificación por visitar nuevas ciudades
                                    if (citiesVisited == 2 && !stamps.any { it.locationName.lowercase() == detectedCity.lowercase() }) {
                                        NotificationHelper(context).showAchievementUnlocked("Explorador Regional", "¡Has visitado 3 ciudades diferentes!")
                                    }

                                    // 5. Notificaciones y mantenimiento
                                    NotificationHelper(context).showNewPassportStamp(detectedCity)
                                    MaintenanceChecker(context).checkStatus()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("FINALIZAR VIAJE", color = Color.White)
                        }
                    }
                }
            }
        }
    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Radio de la Tierra en km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

@Composable
fun CategoryCard(label: String, value: String, icon: ImageVector) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeatherSection(lat: Double, lon: Double) {
    var weatherData by remember { mutableStateOf<WeatherResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(lat, lon) {
        try {
            weatherData = WeatherApiService.create().getWeather(lat, lon, "519ca3ab947f8914a3366ac64cf291fc")
        } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
    }

    if (!isLoading && weatherData != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/${weatherData?.weather?.firstOrNull()?.icon}@2x.png",
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = weatherData?.cityName ?: "Destino", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${weatherData?.main?.temp?.toInt()}°C", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Blue)
                        Text(text = "${weatherData?.main?.humidity}% Hum.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StopItem(number: Int, text: String, isLast: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF7B1FA2), CircleShape), // Morado
                contentAlignment = Alignment.Center
            ) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(30.dp).background(Color.LightGray))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
