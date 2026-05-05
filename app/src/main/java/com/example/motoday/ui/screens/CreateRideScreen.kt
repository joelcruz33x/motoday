package com.example.motoday.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import android.widget.Toast
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.RideEntity
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.models.Document
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRideScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startLoc by remember { mutableStateOf("") }
    var endLoc by remember { mutableStateOf("") }
    var meetingPoint by remember { mutableStateOf("") }
    var scheduledStops by remember { mutableStateOf("") }

    var startLat by remember { mutableDoubleStateOf(0.0) }
    var startLng by remember { mutableDoubleStateOf(0.0) }
    var endLat by remember { mutableDoubleStateOf(0.0) }
    var endLng by remember { mutableDoubleStateOf(0.0) }
    
    var difficulty by remember { mutableStateOf("Fácil") }
    var terrainType by remember { mutableStateOf("Asfalto") }

    // Publisher State
    var isIndependent by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var userGroups by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var expandedPublisher by remember { mutableStateOf(false) }

    var showStartMapPicker by remember { mutableStateOf(false) }
    var showEndMapPicker by remember { mutableStateOf(false) }

    // Load user groups
    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        if (userId != null) {
            userGroups = appwrite.getUserGroups(userId)
        }
    }

    // Date and Time State
    val calendar = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableStateOf(calendar.timeInMillis) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    // Date Picker
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            
            // Show Time Picker after Date
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    selectedDate = calendar.timeInMillis
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planificar Rodada") },
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre de la Ruta") },
                modifier = Modifier.fillMaxWidth()
            )

            // Date and Time Field
            OutlinedTextField(
                value = sdf.format(Date(selectedDate)),
                onValueChange = { },
                label = { Text("Fecha y Hora de Salida") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() }
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción de la Rodada") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // PUNTO DE INICIO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startLoc,
                    onValueChange = { startLoc = it },
                    label = { Text("Ciudad/Punto de Inicio") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showStartMapPicker = true }) {
                            Icon(
                                Icons.Default.LocationOn, 
                                contentDescription = "Mapa Inicio",
                                tint = if (startLat != 0.0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }

            // PUNTO DE DESTINO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = endLoc,
                    onValueChange = { endLoc = it },
                    label = { Text("Ciudad/Punto de Destino") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showEndMapPicker = true }) {
                            Icon(
                                Icons.Default.Flag, 
                                contentDescription = "Mapa Destino",
                                tint = if (endLat != 0.0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }

            if (showStartMapPicker) {
                MapPickerDialog(
                    title = "Ubicar Punto de Inicio",
                    onLocationSelected = { lat, lng ->
                        startLat = lat
                        startLng = lng
                        if (startLoc.isBlank()) startLoc = "Ubicación en mapa"
                        showStartMapPicker = false
                    },
                    onDismiss = { showStartMapPicker = false }
                )
            }

            if (showEndMapPicker) {
                MapPickerDialog(
                    title = "Ubicar Punto de Destino",
                    onLocationSelected = { lat, lng ->
                        endLat = lat
                        endLng = lng
                        if (endLoc.isBlank()) endLoc = "Ubicación en mapa"
                        showEndMapPicker = false
                    },
                    onDismiss = { showEndMapPicker = false }
                )
            }

            OutlinedTextField(
                value = meetingPoint,
                onValueChange = { meetingPoint = it },
                label = { Text("Punto de Encuentro Específico") },
                placeholder = { Text("Ej: Gasolinera Primax Av. de las Américas") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = scheduledStops,
                onValueChange = { scheduledStops = it },
                label = { Text("Paradas Programadas (separadas por coma)") },
                placeholder = { Text("Mirador de Turi, Paute, Gualaceo...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // DIFICULTAD
            Text("Nivel de Dificultad", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Fácil", "Intermedio", "Difícil").forEach { level ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { difficulty = level },
                        label = { Text(level) }
                    )
                }
            }

            // TERRENO
            Text("Tipo de Terreno", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Asfalto", "Mixto", "Off-road").forEach { type ->
                    FilterChip(
                        selected = terrainType == type,
                        onClick = { terrainType = type },
                        label = { Text(type) }
                    )
                }
            }

            // PUBLICADOR (Independiente o Grupo)
            Text("Publicar como:", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxWidth()) {
                val publisherText = if (isIndependent) {
                    "Independiente (Tu nombre)"
                } else {
                    userGroups.firstOrNull { it.id == selectedGroupId }?.data?.get("name")?.toString() ?: "Seleccionar Grupo"
                }

                OutlinedCard(
                    onClick = { expandedPublisher = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isIndependent) Icons.Default.Person else Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(publisherText)
                        }
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }

                DropdownMenu(
                    expanded = expandedPublisher,
                    onDismissRequest = { expandedPublisher = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Independiente (Tu nombre)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        onClick = {
                            isIndependent = true
                            selectedGroupId = null
                            expandedPublisher = false
                        }
                    )
                    if (userGroups.isNotEmpty()) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        userGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.data["name"]?.toString() ?: "Grupo") },
                                leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                                onClick = {
                                    isIndependent = false
                                    selectedGroupId = group.id
                                    expandedPublisher = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        val userId = authManager.getCurrentUserId() ?: "anonymous"
                        val userProfile = db.userDao().getUserProfileOnce()
                        
                        val creatorName = if (isIndependent) {
                            userProfile?.name ?: "MotoDay User"
                        } else {
                            userGroups.firstOrNull { it.id == selectedGroupId }?.data?.get("name")?.toString() ?: "Moto Club"
                        }

                        val stopsList = scheduledStops.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        // 1. Datos para Appwrite
                        val remoteData = mapOf(
                            "title" to title,
                            "description" to description,
                            "date" to selectedDate,
                            "startLocation" to startLoc,
                            "endLocation" to endLoc,
                            "startLat" to startLat,
                            "startLng" to startLng,
                            "endLat" to endLat,
                            "endLng" to endLng,
                            "meetingPoint" to meetingPoint,
                            "scheduledStops" to stopsList,
                            "difficulty" to difficulty,
                            "terrainType" to terrainType,
                            "creatorName" to creatorName,
                            "creatorId" to userId,
                            "status" to "PLANNED",
                            "participantIds" to listOf(userId)
                        )

                        // 2. Crear en Appwrite
                        val remoteId = appwrite.createRemoteRide(remoteData)

                        // 3. Crear Localmente (usando el ID remoto si se pudo crear)
                        val newRide = RideEntity(
                            title = title,
                            description = description,
                            date = selectedDate,
                            startLocation = startLoc,
                            endLocation = endLoc,
                            startLat = startLat,
                            startLng = startLng,
                            endLat = endLat,
                            endLng = endLng,
                            meetingPoint = meetingPoint,
                            scheduledStops = scheduledStops,
                            difficulty = difficulty,
                            terrainType = terrainType,
                            creatorName = creatorName,
                            isAttending = true,
                            participantsCount = 1,
                            isSynced = remoteId != null,
                            remoteId = remoteId,
                            creatorId = userId
                        )
                        db.rideDao().insertRide(newRide)
                        
                        Toast.makeText(context, "¡Rodada publicada con éxito!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && startLoc.isNotBlank() && endLoc.isNotBlank() && meetingPoint.isNotBlank()
            ) {
                Text("Publicar Plan de Ruta")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    title: String,
    onLocationSelected: (Double, Double) -> Unit, 
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Configuración de OSM
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osm", 0))
    }

    var selectedPoint by remember { mutableStateOf(GeoPoint(-1.6637, -78.6546)) } // Centrado en Ecuador aprox

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onLocationSelected(selectedPoint.latitude, selectedPoint.longitude) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FFFF), // PrimaryCyan
                    contentColor = Color(0xFF6200EE)    // Purple
                )
            ) {
                Text("Confirmar Ubicación")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FFFF), // PrimaryCyan
                    contentColor = Color(0xFF6200EE)    // Purple
                )
            ) {
                Text("Cancelar")
            }
        },
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(6.0)
                            controller.setCenter(selectedPoint)
                            
                            val marker = Marker(this)
                            marker.position = selectedPoint
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = title
                            overlays.add(marker)

                            setOnTouchListener { v, event ->
                                if (event.action == android.view.MotionEvent.ACTION_UP) {
                                    val proj = projection
                                    val geoPoint = proj.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                                    selectedPoint = geoPoint
                                    marker.position = geoPoint
                                    invalidate()
                                }
                                false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        // Actualizar si es necesario
                    }
                )
            }
        }
    )
}
