package com.example.motoday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.motoday.ui.components.BottomNavigationBar
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.MaintenanceEntity
import com.example.motoday.data.local.entities.UserEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.models.Document
import android.util.Log

import com.example.motoday.ui.utils.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val logs by db.maintenanceDao().getAllLogs().collectAsState(initial = emptyList())
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    val notificationHelper = remember { NotificationHelper(context) }
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }

    val bikes by db.bikeDao().getAllBikes().collectAsState(initial = emptyList())
    var selectedBikeId by remember { mutableStateOf<Int?>(null) }
    
    // Auto-seleccionar la moto principal basándonos en el perfil
    LaunchedEffect(userProfile, bikes) {
        if (selectedBikeId == null && userProfile != null && bikes.isNotEmpty()) {
            val mainBike = bikes.find { it.model == userProfile?.bikeModel }
            if (mainBike != null) {
                selectedBikeId = mainBike.id
            }
        }
    }

    // Descargar mantenimientos remotos si local está vacío
    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        if (userId != null) {
            val localLogs = db.maintenanceDao().getAllLogsOnce()
            if (localLogs.isEmpty()) {
                val remoteDocs = appwrite.getUserMaintenanceLogs(userId)
                if (remoteDocs.isNotEmpty()) {
                    val currentBikes = db.bikeDao().getAllBikesOnce()
                    val entities = remoteDocs.map { doc ->
                        val remoteBikeId = doc.data["bikeId"] as? String
                        val localId = currentBikes.find { it.remoteId == remoteBikeId }?.id
                        
                        MaintenanceEntity(
                            bikeId = localId,
                            date = (doc.data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            type = doc.data["type"] as? String ?: "General",
                            mileage = (doc.data["mileage"] as? Number)?.toInt() ?: 0,
                            description = doc.data["description"] as? String ?: "",
                            cost = (doc.data["cost"] as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    db.maintenanceDao().insertLogs(entities)
                }
            }
        }
    }

    val bikesMap = remember(bikes) { bikes.associateBy({ it.id }, { it.model }) }
    val selectedBike = bikes.find { it.id == selectedBikeId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hoja de Vida") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        notificationHelper.showMaintenanceAlert(
                            "Prueba de Notificación", 
                            "¡Funciona! Así recibirás tus alertas de mantenimiento."
                        )
                    }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Probar Alerta", tint = Color(0xFF00FFFF))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF00FFFF),
                contentColor = Color(0xFF6200EE)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Registro")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (bikes.isNotEmpty()) {
                var expandedBikes by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = { expandedBikes = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TwoWheeler, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedBike?.model ?: "Salud Global")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expandedBikes, onDismissRequest = { expandedBikes = false }) {
                        DropdownMenuItem(
                            text = { Text("General (Perfil)") },
                            onClick = { selectedBikeId = null; expandedBikes = false }
                        )
                        bikes.forEach { bike ->
                            DropdownMenuItem(
                                text = { Text("${bike.model} (${bike.year})") },
                                onClick = { selectedBikeId = bike.id; expandedBikes = false }
                            )
                        }
                    }
                }
            }

            HealthSummarySection(userProfile, logs, selectedBike, bikes) { type, mileage, bikeId ->
                scope.launch {
                    val finalBikeId = bikeId ?: (bikes.find { it.model == userProfile?.bikeModel }?.id)
                    
                    db.maintenanceDao().insertLog(
                        MaintenanceEntity(
                            bikeId = finalBikeId,
                            date = System.currentTimeMillis(),
                            type = type,
                            mileage = mileage,
                            description = "Reinicio rápido de $type",
                            cost = 0.0
                        )
                    )
                    
                    val userId = authManager.getCurrentUserId()
                    if (userId != null) {
                        val bikeToSync = bikes.find { it.id == finalBikeId }
                        val remoteBikeId = bikeToSync?.remoteId ?: finalBikeId?.toString()
                        
                        appwrite.syncMaintenance(
                            userId = userId,
                            bikeId = remoteBikeId,
                            type = type,
                            mileage = mileage,
                            description = "Reinicio rápido de $type",
                            cost = 0.0,
                            date = System.currentTimeMillis()
                        )
                    }

                    if (finalBikeId != null) {
                        val bikeToUpdate = bikes.find { it.id == finalBikeId }
                        if (bikeToUpdate != null && mileage > bikeToUpdate.currentKm) {
                            db.bikeDao().insertOrUpdate(bikeToUpdate.copy(currentKm = mileage))
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay registros aún.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { 
                        Text("Historial de Mantenimientos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(logs) { log ->
                        val bikeName = bikesMap[log.bikeId]
                        MaintenanceCard(log, bikeName) {
                            scope.launch { db.maintenanceDao().deleteLog(log.id) }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddMaintenanceDialog(
                bikes = bikes,
                onDismiss = { showAddDialog = false },
                onSave = { type, mileage, desc, cost, bikeId ->
                    scope.launch {
                        val finalBikeId = if (bikeId == null && userProfile != null) {
                            bikes.find { it.model == userProfile?.bikeModel }?.id
                        } else {
                            bikeId
                        }

                        db.maintenanceDao().insertLog(
                            MaintenanceEntity(
                                bikeId = finalBikeId,
                                date = System.currentTimeMillis(),
                                type = type,
                                mileage = mileage,
                                description = desc,
                                cost = cost
                            )
                        )
                        
                        val userId = authManager.getCurrentUserId()
                        if (userId != null) {
                            val bikeToSync = bikes.find { it.id == finalBikeId }
                            val remoteBikeId = bikeToSync?.remoteId ?: finalBikeId?.toString()
                            
                            appwrite.syncMaintenance(
                                userId = userId,
                                bikeId = remoteBikeId,
                                type = type,
                                mileage = mileage,
                                description = desc,
                                cost = cost,
                                date = System.currentTimeMillis()
                            )
                        }
                        
                        if (finalBikeId != null) {
                            val bikeToUpdate = bikes.find { it.id == finalBikeId }
                            if (bikeToUpdate != null) {
                                db.bikeDao().insertOrUpdate(bikeToUpdate.copy(currentKm = mileage))
                            }
                        }

                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun HealthSummarySection(
    user: UserEntity?, 
    logs: List<MaintenanceEntity>, 
    selectedBike: com.example.motoday.data.local.entities.BikeEntity? = null, 
    bikes: List<com.example.motoday.data.local.entities.BikeEntity> = emptyList(),
    onQuickReset: (String, Int, Int?) -> Unit
) {
    val principalBike = if (selectedBike == null && user != null) {
        bikes.find { it.model == user.bikeModel && it.year == user.bikeYear }
    } else {
        selectedBike
    }

    val currentKm = principalBike?.currentKm ?: (user?.totalKilometers ?: 0)
    
    val filteredLogs = if (principalBike != null) {
        logs.filter { it.bikeId == principalBike.id }
    } else {
        logs
    }
    
    val lastOilKm = filteredLogs.filter { it.type == "Aceite" }.maxByOrNull { it.date }?.mileage ?: 0
    val lastTiresKm = filteredLogs.filter { it.type == "Llantas" }.maxByOrNull { it.date }?.mileage ?: 0

    val oilInterval = 3000
    val tiresInterval = 12000

    val oilRemaining = (lastOilKm + oilInterval) - currentKm
    val tiresRemaining = (lastTiresKm + tiresInterval) - currentKm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val title = if (selectedBike == null && principalBike != null) 
            "Salud: ${principalBike.model} (Principal)" 
        else if (selectedBike != null) 
            "Salud: ${selectedBike.model}"
        else 
            "Estado de Salud Global"

        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        
        HealthBar(
            label = "Aceite de Motor",
            remainingKm = oilRemaining,
            totalInterval = oilInterval,
            icon = Icons.Default.OilBarrel,
            onReset = { onQuickReset("Aceite", currentKm, principalBike?.id) }
        )
        
        HealthBar(
            label = "Estado de Llantas",
            remainingKm = tiresRemaining,
            totalInterval = tiresInterval,
            icon = Icons.Default.TireRepair,
            onReset = { onQuickReset("Llantas", currentKm, principalBike?.id) }
        )

        if (oilRemaining < 0 || tiresRemaining < 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Revisa los registros. El kilometraje actual parece ser menor al último mantenimiento.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun HealthBar(label: String, remainingKm: Int, totalInterval: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onReset: () -> Unit) {
    val progress = (remainingKm.toFloat() / totalInterval).coerceIn(0f, 1f)
    val color = when {
        progress > 0.5f -> Color(0xFF4CAF50)
        progress > 0.2f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (remainingKm > 0) "Faltan $remainingKm km" else "¡Vencido!", 
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onReset, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Reiniciar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).background(Color.LightGray, RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun MaintenanceCard(log: MaintenanceEntity, bikeName: String?, onDelete: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (log.type) {
                            "Aceite" -> Icons.Default.OilBarrel
                            "Frenos" -> Icons.Default.Settings
                            "Llantas" -> Icons.Default.TireRepair
                            else -> Icons.Default.Build
                        },
                        contentDescription = null,
                        tint = Color(0xFF6200EE),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(log.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                if (bikeName != null) {
                    Text("Moto: $bikeName", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6200EE))
                }

                Text("${log.mileage} km • ${sdf.format(Date(log.date))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(log.description, style = MaterialTheme.typography.bodyMedium)
                if (log.cost > 0) {
                    Text("$${log.cost}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaintenanceDialog(
    bikes: List<com.example.motoday.data.local.entities.BikeEntity>,
    onDismiss: () -> Unit, 
    onSave: (String, Int, String, Double, Int?) -> Unit
) {
    var type by remember { mutableStateOf("Mantenimiento General (ABC)") }
    var mileage by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var selectedBikeId by remember { mutableStateOf<Int?>(null) }

    val types = listOf("Aceite", "Frenos", "Llantas", "Kit Arrastre", "Mantenimiento General (ABC)")
    var expandedType by remember { mutableStateOf(false) }
    var expandedBikes by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Mantenimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bikes.isNotEmpty()) {
                    Box {
                        OutlinedTextField(
                            value = bikes.find { it.id == selectedBikeId }?.model ?: "Moto Principal",
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Moto") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { expandedBikes = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }
                        )
                        DropdownMenu(expanded = expandedBikes, onDismissRequest = { expandedBikes = false }) {
                            DropdownMenuItem(
                                text = { Text("Moto Principal (Auto)") },
                                onClick = { selectedBikeId = null; expandedBikes = false }
                            )
                            bikes.forEach { bike ->
                                DropdownMenuItem(
                                    text = { Text("${bike.model} (${bike.year})") },
                                    onClick = { selectedBikeId = bike.id; expandedBikes = false }
                                )
                            }
                        }
                    }
                }

                Box {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Tipo de Mantenimiento") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedType = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                    )
                    DropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; expandedType = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it },
                    label = { Text("Kilometraje en el tablero") },
                    placeholder = { Text("Ej: 12500") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Km que marca la moto en este momento") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Costo ($)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(type, mileage.toIntOrNull() ?: 0, desc, cost.toDoubleOrNull() ?: 0.0, selectedBikeId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color(0xFF6200EE))
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
