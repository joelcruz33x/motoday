package com.example.motoday.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.local.entities.ContactEntity
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import io.appwrite.models.Document
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val contacts by db.contactDao().getAllContacts().collectAsState(initial = emptyList())
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }

    var emergencySent by remember { mutableStateOf(false) }
    var selectedHelpType by remember { mutableStateOf<String?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var currentCoords by remember { mutableStateOf("Esperando permisos...") }

    // Descargar contactos remotos si local está vacío
    LaunchedEffect(Unit) {
        val userId = authManager.getCurrentUserId()
        if (userId != null) {
            val localContacts = db.contactDao().getAllContactsOnce()
            if (localContacts.isEmpty()) {
                val remoteDocs = appwrite.getUserContacts(userId)
                if (remoteDocs.isNotEmpty()) {
                    val entities = remoteDocs.map { doc ->
                        ContactEntity(
                            name = doc.data["name"] as? String ?: "",
                            phoneNumber = doc.data["phoneNumber"] as? String ?: "",
                            relationship = doc.data["relationship"] as? String ?: "Emergencia"
                        )
                    }
                    db.contactDao().insertContacts(entities)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            currentCoords = "Obteniendo ubicación..."
        } else {
            currentCoords = "Permiso de GPS denegado"
        }
    }

    fun fetchLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentCoords = "${location.latitude},${location.longitude}"
                } else {
                    fusedLocationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).addOnSuccessListener { newLocation ->
                        currentCoords = if (newLocation != null) {
                            "${newLocation.latitude},${newLocation.longitude}"
                        } else {
                            "Ubicación no disponible"
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            currentCoords = "Sin permisos de GPS"
        }
    }

    // Pedir permisos al iniciar
    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            currentCoords = "Obteniendo ubicación..."
            fetchLocation()
        }
    }

    // Reintentar si cambia a "Obteniendo ubicación..."
    LaunchedEffect(currentCoords) {
        if (currentCoords == "Obteniendo ubicación...") {
            fetchLocation()
        }
    }

    fun sendEmergencyAlert() {
        if (contacts.isEmpty()) {
            Toast.makeText(context, "Añade al menos un contacto de emergencia", Toast.LENGTH_LONG).show()
            return
        }

        // VALIDACIÓN: Evitar enviar si el GPS no ha respondido todavía
        if (currentCoords == "Obteniendo ubicación..." || currentCoords.contains("no disponible")) {
            Toast.makeText(context, "Esperando señal GPS válida...", Toast.LENGTH_SHORT).show()
            return
        }

        val name = userProfile?.name ?: "un motero"
        val mapsLink = "https://www.google.com/maps/search/?api=1&query=$currentCoords"
        
        val message = when (selectedHelpType) {
            "Mecánica" -> "soy $name, necesito ayuda mecanica con mi moto en la siguiente ubicacion: $mapsLink"
            "Accidente" -> "soy $name, necesito ayuda, tuve un accidente en la siguiente ubicacion: $mapsLink"
            "Gasolina" -> "soy $name, necesito ayuda, me quede sin gasolina en la siguiente ubicacion: $mapsLink"
            else -> "🚨 SOS MOTODAY 🚨\nSoy $name. Necesito ayuda por: ${selectedHelpType?.uppercase()}.\nUbicación: $mapsLink"
        }

        contacts.forEach { contact ->
            try {
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                // Si falla el SMS (por permisos o falta de SIM), intentamos abrir WhatsApp como backup
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://wa.me/${contact.phoneNumber}?text=${Uri.encode(message)}")
                context.startActivity(intent)
            }
        }
        emergencySent = true
        Toast.makeText(context, "Alertas enviadas a ${contacts.size} contactos", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Centro de Emergencias", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                if (!emergencySent) {
                    Text(
                        if (selectedHelpType == null) "ELIGE UN MOTIVO ABAJO" else "MANTÉN PRESIONADO PARA ENVIAR",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedHelpType == null) Color.Gray else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Surface(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .clickable(enabled = selectedHelpType != null) { sendEmergencyAlert() }
                            .background(if (selectedHelpType != null) MaterialTheme.colorScheme.error else Color.Gray),
                        color = if (selectedHelpType != null) MaterialTheme.colorScheme.error else Color.Gray,
                        shape = CircleShape,
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                                Text("S.O.S", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(48.dp))
                            Text("¡ALERTA ENVIADA!", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${userProfile?.name ?: "Un motero"} necesita ayuda con: ${selectedHelpType?.uppercase()}",
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = Color(0xFFB71C1C)
                            )
                            Text(
                                text = "Moteros cercanos han recibido tu ubicación.",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                            TextButton(onClick = { 
                                emergencySent = false 
                                selectedHelpType = null
                            }) { Text("Cancelar Alerta", color = Color.Gray) }
                        }
                    }
                }
            }

            item {
                EmergencyTypeSection(selectedHelpType) { selectedHelpType = it }
            }

            item {
                Text(
                    "Contactos de Emergencia",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            // Lista de contactos de la DB
            items(contacts) { contact ->
                EmergencyContactItem(contact, onCall = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                    context.startActivity(intent)
                }, onDelete = {
                    scope.launch { db.contactDao().deleteContact(contact) }
                })
            }

            item {
                OutlinedButton(
                    onClick = { showAddContactDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir Contacto")
                }
            }
            
            item {
                LocationCard(currentCoords, onRetry = { 
                    currentCoords = "Obteniendo ubicación..."
                    fetchLocation()
                })
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onSave = { name, phone, relation ->
                scope.launch {
                    // Guardar local
                    db.contactDao().insertContact(ContactEntity(name = name, phoneNumber = phone, relationship = relation))
                    
                    // Sincronizar remoto
                    val userId = authManager.getCurrentUserId()
                    if (userId != null) {
                        appwrite.syncContact(userId, name, phone, relation)
                    }

                    showAddContactDialog = false
                }
            }
        )
    }
}

@Composable
fun EmergencyContactItem(contact: ContactEntity, onCall: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${contact.relationship} • ${contact.phoneNumber}") },
        leadingContent = { 
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
        },
        trailingContent = {
            Row {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray) }
                IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Color(0xFF4CAF50)) }
            }
        },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    )
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Contacto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") })
                OutlinedTextField(value = relation, onValueChange = { relation = it }, label = { Text("Relación (Ej: Mecánico)") })
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank() && phone.isNotBlank()) onSave(name, phone, relation) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EmergencyTypeSection(selected: String?, onSelect: (String) -> Unit) {
    Column {
        Text("¿Qué tipo de ayuda necesitas?", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmergencyTypeChip("Mecánica", Icons.Default.Settings, selected == "Mecánica", Modifier.weight(1f)) { onSelect("Mecánica") }
            EmergencyTypeChip("Accidente", Icons.Default.Warning, selected == "Accidente", Modifier.weight(1f)) { onSelect("Accidente") }
            EmergencyTypeChip("Gasolina", Icons.Default.LocalGasStation, selected == "Gasolina", Modifier.weight(1f)) { onSelect("Gasolina") }
        }
    }
}

@Composable
fun LocationCard(coords: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ubicación actual", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(coords, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            IconButton(onClick = onRetry) { 
                Icon(Icons.Default.Refresh, contentDescription = "Reintentar", tint = MaterialTheme.colorScheme.primary) 
            }
        }
    }
}

@Composable
fun EmergencyTypeChip(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.error else Color.LightGray),
        color = if (isSelected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                label, 
                fontSize = 12.sp, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
