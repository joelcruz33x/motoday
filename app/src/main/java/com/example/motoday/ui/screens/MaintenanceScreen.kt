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
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.MaintenanceEntity
import com.example.motoday.data.local.entities.UserEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
                containerColor = Color(0xFF00FFFF), // PrimaryCyan
                contentColor = Color(0xFF6200EE)    // Purple
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Registro")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Panel de Salud de la Moto
            HealthSummarySection(userProfile, logs)

            if (logs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay registros aún. ¡Añade tu mantenimiento!", color = Color.Gray)
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
                        MaintenanceCard(log) {
                            scope.launch { db.maintenanceDao().deleteLog(log.id) }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddMaintenanceDialog(
                onDismiss = { showAddDialog = false },
                onSave = { type, mileage, desc, cost ->
                    scope.launch {
                        // 1. Guardar el log de mantenimiento
                        db.maintenanceDao().insertLog(
                            MaintenanceEntity(
                                date = System.currentTimeMillis(),
                                type = type,
                                mileage = mileage,
                                description = desc,
                                cost = cost
                            )
                        )
                        
                        // 2. ACTUALIZACIÓN AUTOMÁTICA: 
                        // Si el kilometraje del mantenimiento es mayor al actual, actualizamos el perfil
                        userProfile?.let { currentProfile ->
                            if (mileage > currentProfile.totalKilometers) {
                                db.userDao().insertOrUpdate(
                                    currentProfile.copy(totalKilometers = mileage)
                                )
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
fun HealthSummarySection(user: UserEntity?, logs: List<MaintenanceEntity>) {
    val currentKm = user?.totalKilometers ?: 0
    
    // Buscar último cambio de aceite y llantas
    val lastOilKm = logs.filter { it.type == "Aceite" }.maxByOrNull { it.mileage }?.mileage ?: 0
    val lastTiresKm = logs.filter { it.type == "Llantas" }.maxByOrNull { it.mileage }?.mileage ?: 0

    val oilInterval = 3000
    val tiresInterval = 15000

    val oilRemaining = (lastOilKm + oilInterval) - currentKm
    val tiresRemaining = (lastTiresKm + tiresInterval) - currentKm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Estado de Salud", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        
        HealthBar(
            label = "Aceite de Motor",
            remainingKm = oilRemaining,
            totalInterval = oilInterval,
            icon = Icons.Default.OilBarrel
        )
        
        HealthBar(
            label = "Estado de Llantas",
            remainingKm = tiresRemaining,
            totalInterval = tiresInterval,
            icon = Icons.Default.TireRepair
        )
    }
}

@Composable
fun HealthBar(label: String, remainingKm: Int, totalInterval: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val progress = (remainingKm.toFloat() / totalInterval).coerceIn(0f, 1f)
    val color = when {
        progress > 0.5f -> Color(0xFF4CAF50) // Verde
        progress > 0.2f -> Color(0xFFFF9800) // Naranja
        else -> Color(0xFFF44336) // Rojo
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
fun MaintenanceCard(log: MaintenanceEntity, onDelete: () -> Unit) {
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
fun AddMaintenanceDialog(onDismiss: () -> Unit, onSave: (String, Int, String, Double) -> Unit) {
    var type by remember { mutableStateOf("Mantenimiento General (ABC)") }
    var mileage by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }

    val types = listOf("Aceite", "Frenos", "Llantas", "Kit Arrastre", "Mantenimiento General (ABC)")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Mantenimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Tipo") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it },
                    label = { Text("Kilometraje actual de la moto") },
                    placeholder = { Text("Ej: 12500") },
                    modifier = Modifier.fillMaxWidth(),
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
                onClick = { onSave(type, mileage.toIntOrNull() ?: 0, desc, cost.toDoubleOrNull() ?: 0.0) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color(0xFF6200EE))
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
