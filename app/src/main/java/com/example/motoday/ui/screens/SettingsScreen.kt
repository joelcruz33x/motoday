package com.example.motoday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.motoday.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    
    val userProfile by db.userDao().getUserProfile().collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        userProfile?.let { user ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    SettingsSectionTitle("Preferencias de la App")
                    
                    SettingsToggleItem(
                        title = "Usar Millas",
                        subtitle = "Cambiar de Kilómetros a Millas",
                        icon = Icons.Default.Straighten,
                        checked = user.useMiles,
                        onCheckedChange = { newValue ->
                            scope.launch {
                                db.userDao().insertOrUpdate(user.copy(useMiles = newValue))
                            }
                        }
                    )
                    
                    SettingsToggleItem(
                        title = "Notificaciones",
                        subtitle = "Alertas de mantenimiento y logros",
                        icon = Icons.Default.Notifications,
                        checked = user.notificationsEnabled,
                        onCheckedChange = { newValue ->
                            scope.launch {
                                db.userDao().insertOrUpdate(user.copy(notificationsEnabled = newValue))
                            }
                        }
                    )
                }

                item {
                    SettingsSectionTitle("Seguridad y SOS")
                    
                    SettingsClickItem(
                        title = "Contactos de Emergencia",
                        subtitle = "Gestionar números para alertas SOS",
                        icon = Icons.Default.Phone,
                        onClick = { navController.navigate("sos") }
                    )
                }

                item {
                    SettingsSectionTitle("Cuenta y Datos")
                    
                    SettingsClickItem(
                        title = "Editar Perfil de Moto",
                        subtitle = "Actualizar modelo, año y specs",
                        icon = Icons.Default.TwoWheeler,
                        onClick = { /* Navegar a edición de perfil */ }
                    )

                    SettingsClickItem(
                        title = "Borrar Historial de Rutas",
                        subtitle = "Esta acción no se puede deshacer",
                        icon = Icons.Default.DeleteForever,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = { /* Lógica para borrar */ }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "MotoDay v1.0.4 - Beta",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium, color = contentColor) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = 0.7f)) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray) }
    )
}
