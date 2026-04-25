package com.example.motoday.ui.screens

import android.content.Intent
import android.net.Uri
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
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
import com.example.motoday.ui.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.atan2

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
                        Text(
                            text = currentRide.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val diffColor = when (currentRide.difficulty) {
                                    "Fácil" -> Color(0xFF4CAF50)
                                    "Intermedio" -> Color(0xFFFF9800)
                                    "Difícil" -> Color(0xFFF44336)
                                    else -> null
                                }
                                CategoryCard("Dificultad", currentRide.difficulty, Icons.Default.TrendingUp, diffColor)
                                CategoryCard("Terreno", currentRide.terrainType, Icons.Default.Landscape, Color(0xFF757575))
                            }
                            
                            if (currentRide.status == "PLANNED") {
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
                            } else if (!currentRide.isAttending) {
                                // Mensaje de advertencia si no asistió y ya inició
                                Surface(
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                                ) {
                                    Text(
                                        text = "Lo siento, la ruta ya ha iniciado",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = currentRide.description, style = MaterialTheme.typography.bodyLarge)
                        
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        DetailRow(Icons.Default.Place, "Inicio: ${currentRide.startLocation}")
                        DetailRow(Icons.Default.LocationOn, "Destino: ${currentRide.endLocation}")
                        
                        val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                        DetailRow(Icons.Default.Event, "Fecha: ${sdf.format(Date(currentRide.date))}")
                        
                        DetailRow(Icons.Default.Flag, "Punto de encuentro: ${currentRide.meetingPoint}")

                        if (currentRide.startLat != 0.0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=${currentRide.startLat},${currentRide.startLng}")
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
                    }
                }

                if (currentRide.scheduledStops.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Paradas Programadas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val stops = currentRide.scheduledStops.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    stops.forEachIndexed { index, stop ->
                        StopItem(number = index + 1, name = stop, isLast = index == stops.size - 1)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (currentRide.status != "COMPLETED") {
                    if (currentRide.status == "PLANNED") {
                        Button(
                            onClick = { scope.launch { db.rideDao().updateRide(currentRide.copy(status = "ONGOING")) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COMENZAR RUTA")
                        }
                    } else if (currentRide.status == "ONGOING") {
                        Button(
                            onClick = {
                                scope.launch {
                                    val distanceKm = if (currentRide.startLat != 0.0 && currentRide.endLat != 0.0) {
                                        calculateDistance(currentRide.startLat, currentRide.startLng, currentRide.endLat, currentRide.endLng).toInt()
                                    } else 50

                                    db.rideDao().updateRide(currentRide.copy(status = "COMPLETED", completedAt = System.currentTimeMillis()))
                                    
                                    val user = db.userDao().getUserProfileOnce()
                                    user?.let { currentProfile ->
                                        val newKms = currentProfile.totalKilometers + distanceKm
                                        val newRides = currentProfile.ridesCompleted + 1
                                        db.userDao().insertOrUpdate(currentProfile.copy(ridesCompleted = newRides, totalKilometers = newKms))

                                        // ACTUALIZACIÓN DE LA MOTO PRINCIPAL
                                        scope.launch(Dispatchers.IO) {
                                            val userId = authManager.getCurrentUserId()
                                            if (userId != null) {
                                                // 1. Sincronizar Perfil en Appwrite
                                                try {
                                                    val profilePicId = appwrite.extractFileIdFromUrl(currentProfile.profilePictureUri)
                                                    val bikePicId = appwrite.extractFileIdFromUrl(currentProfile.bikePictureUri)
                                                    appwrite.updateUserProfile(
                                                        userId = userId,
                                                        name = currentProfile.name,
                                                        level = currentProfile.level,
                                                        bikeModel = currentProfile.bikeModel,
                                                        bikeSpecs = currentProfile.bikeSpecs,
                                                        bikeYear = currentProfile.bikeYear,
                                                        bikeColor = currentProfile.bikeColor,
                                                        totalKm = newKms,
                                                        rides = newRides,
                                                        isIndependent = currentProfile.isIndependent,
                                                        profilePic = profilePicId,
                                                        bikePic = bikePicId
                                                    )
                                                } catch (e: Exception) { Log.e("RideDetail", "Error sync profile: ${e.message}") }

                                                // 2. Buscar y actualizar la moto principal local y remotamente
                                                val bikes = db.bikeDao().getAllBikesOnce()
                                                val mainBike = bikes.find { 
                                                    it.model == currentProfile.bikeModel && 
                                                    it.year == currentProfile.bikeYear 
                                                }
                                                
                                                if (mainBike != null) {
                                                    val updatedBikeKm = mainBike.currentKm + distanceKm
                                                    val updatedBike = mainBike.copy(currentKm = updatedBikeKm)
                                                    
                                                    // Actualizar Local
                                                    db.bikeDao().insertOrUpdate(updatedBike)
                                                    
                                                    // Actualizar Remoto en Appwrite
                                                    mainBike.remoteId?.let { rid ->
                                                        try {
                                                            appwrite.updateRemoteBike(
                                                                bikeId = rid,
                                                                model = updatedBike.model,
                                                                year = updatedBike.year,
                                                                color = updatedBike.color,
                                                                specs = updatedBike.specs,
                                                                status = updatedBike.status,
                                                                currentKm = updatedBikeKm,
                                                                picId = null
                                                            )
                                                        } catch (e: Exception) { Log.e("RideDetail", "Error sync bike: ${e.message}") }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    var detectedCity = currentRide.endLocation
                                    try {
                                        val geocoder = Geocoder(context, Locale.getDefault())
                                        val addresses = withContext(Dispatchers.IO) {
                                            geocoder.getFromLocation(currentRide.endLat, currentRide.endLng, 1)
                                        }
                                        val city = addresses?.firstOrNull()?.locality ?: addresses?.firstOrNull()?.subAdminArea
                                        if (city != null) detectedCity = city
                                    } catch (e: Exception) { }

                                    val iconStamp = when {
                                        detectedCity.contains("Machala", true) -> "ic_stamp_machala"
                                        detectedCity.contains("Guayaquil", true) -> "ic_stamp_guayaquil"
                                        detectedCity.contains("Cuenca", true) -> "ic_stamp_cuenca"
                                        detectedCity.contains("Quito", true) -> "ic_stamp_quito"
                                        else -> "ic_stamp_default"
                                    }

                                    val newStamp = PassportStampEntity(
                                        rideId = currentRide.id,
                                        rideTitle = currentRide.title,
                                        locationName = detectedCity,
                                        iconResName = iconStamp,
                                        date = System.currentTimeMillis()
                                    )
                                    db.passportDao().insertStamp(newStamp)
                                    
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val uId = authManager.getCurrentUserId()
                                            if (uId != null) appwrite.syncStamp(uId, newStamp)
                                        } catch (e: Exception) { }
                                    }
                                    
                                    Toast.makeText(context, "¡Ruta finalizada en $detectedCity!", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
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
fun CategoryCard(label: String, value: String, icon: ImageVector, containerColor: Color? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                null, 
                modifier = Modifier.size(16.dp), 
                tint = if (containerColor != null) Color.White else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold,
                color = if (containerColor != null) Color.White else Color.Unspecified
            )
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StopItem(number: Int, name: String, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(), 
                    color = Color(0xFF6200EE), // Color morado para contraste
                    style = MaterialTheme.typography.labelSmall, 
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
