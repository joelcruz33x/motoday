package com.example.motoday.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.appwrite.models.Document
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(navController: NavController, groupId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appwrite = remember { AppwriteManager.getInstance(context) }
    val authManager = remember { AuthManager(context) }

    var groupDoc by remember { mutableStateOf<Document<Map<String, Any>>?>(null) }
    var membersProfiles by remember { mutableStateOf<List<Document<Map<String, Any>>>>(emptyList()) }
    var currentUserId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Estados para edición
    var name by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    val gson = remember { Gson() }

    val availableRoles = listOf("Presidente", "Vicepresidente", "Sargento de Armas", "Secretario", "Tesorero", "Capitán de Ruta", "Prospecto")

    LaunchedEffect(groupId) {
        currentUserId = authManager.getCurrentUserId() ?: ""
        val groups = appwrite.getGroups()
        val doc = groups.find { it.id == groupId }
        if (doc != null) {
            groupDoc = doc
            name = doc.data["name"] as? String ?: ""
            photoUrl = doc.data["photoUrl"] as? String
            
            val memberIds = (doc.data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            membersProfiles = appwrite.getUsersProfiles(memberIds)
        }
        isLoading = false
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val fileId = appwrite.uploadImage(
                            io.appwrite.models.InputFile.fromBytes(
                                bytes = bytes,
                                filename = "group_update_$groupId.jpg",
                                mimeType = "image/jpeg"
                            )
                        )
                        photoUrl = fileId
                        appwrite.updateGroup(groupId, name, fileId)
                        Toast.makeText(context, "Foto actualizada", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes del Grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (groupDoc != null) {
            val isAdmin = currentUserId == groupDoc?.data?.get("adminId")
            val rolesJson = groupDoc?.data?.get("roles") as? String ?: "{}"
            val type = object : TypeToken<Map<String, String>>() {}.type
            val rolesMap: Map<String, String> = gson.fromJson(rolesJson, type) ?: emptyMap()

            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // SECCIÓN 1: INFORMACIÓN Y EDICIÓN (Solo Admin puede editar)
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(enabled = isAdmin) { imageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = appwrite.getImageUrl(photoUrl!!),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            if (isAdmin) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(4.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isAdmin) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nombre del Grupo") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            if (appwrite.updateGroup(groupId, name, photoUrl)) {
                                                Toast.makeText(context, "Nombre actualizado", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) { Icon(Icons.Default.Save, contentDescription = null) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // SECCIÓN 2: ASIGNAR ROLES (Solo visible para Admin)
                if (isAdmin) {
                    item {
                        Text("Gestionar Cargos / Roles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                membersProfiles.filter { it.id != currentUserId }.forEach { member ->
                                    val memberId = member.id
                                    val memberName = member.data["name"] as? String ?: "Motero"
                                    val currentRole = rolesMap[memberId]
                                    
                                    var showRoleMenu by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(memberName, fontWeight = FontWeight.Bold)
                                            Text(currentRole ?: "Sin cargo", fontSize = 12.sp, color = if (currentRole != null) MaterialTheme.colorScheme.primary else Color.Gray)
                                        }
                                        
                                        Box {
                                            Button(
                                                onClick = { showRoleMenu = true },
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Asignar", fontSize = 12.sp)
                                            }
                                            DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                                                availableRoles.forEach { role ->
                                                    DropdownMenuItem(
                                                        text = { Text(role) },
                                                        onClick = {
                                                            scope.launch {
                                                                if (appwrite.updateMemberRole(groupId, memberId, role)) {
                                                                    // Recargar datos
                                                                    groupDoc = appwrite.getGroup(groupId)
                                                                }
                                                            }
                                                            showRoleMenu = false
                                                        }
                                                    )
                                                }
                                                Divider()
                                                DropdownMenuItem(
                                                    text = { Text("Quitar Cargo", color = Color.Red) },
                                                    onClick = {
                                                        scope.launch {
                                                            if (appwrite.updateMemberRole(groupId, memberId, null)) {
                                                                groupDoc = appwrite.getGroup(groupId)
                                                            }
                                                        }
                                                        showRoleMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (membersProfiles.size <= 1) {
                                    Text("No hay otros miembros para asignar roles.", modifier = Modifier.padding(16.dp), fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // SECCIÓN 3: LISTA DE MIEMBROS (Lectura)
                item {
                    Text("Miembros (${membersProfiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(membersProfiles) { member ->
                    val memberId = member.id
                    val memberName = member.data["name"] as? String ?: "Motero"
                    val memberPhoto = member.data["profilePic"] as? String
                    val memberRole = rolesMap[memberId]
                    val memberLevel = member.data["level"] as? String ?: "Novato"

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!memberPhoto.isNullOrEmpty()) {
                                AsyncImage(
                                    model = appwrite.getImageUrl(memberPhoto),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(memberName, fontWeight = FontWeight.SemiBold)
                                if (memberId == groupDoc?.data?.get("adminId")) {
                                    Icon(Icons.Default.Stars, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp), tint = Color(0xFFFFB100))
                                }
                            }
                            Text("$memberLevel • ${memberRole ?: "Miembro"}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}
