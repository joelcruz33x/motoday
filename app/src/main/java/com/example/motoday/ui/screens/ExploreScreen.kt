package com.example.motoday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val rides by db.rideDao().getAllRides().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explorar Rodadas") }
            )
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
            item {
                Text(text = "Rodadas Programadas", style = MaterialTheme.typography.headlineSmall)
            }
            
            if (rides.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No hay rodadas programadas. ¡Crea una!", color = Color.Gray)
                    }
                }
            }

            items(rides.size) { index ->
                val ride = rides[index]
                val sdf = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.getDefault())
                val dateString = sdf.format(Date(ride.date))

                RideCard(
                    title = ride.title,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideCard(title: String, date: String, location: String, status: String, participantsCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                        color = when (status) {
                            "Asistiré", "Finalizada", "Programada" -> Color.White
                            else -> Color(0xFF6200EE)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = date, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = location, style = MaterialTheme.typography.bodySmall)
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF6200EE)
                )
            ) {
                Text("Ver Detalles de la Ruta", fontWeight = FontWeight.Bold)
            }
        }
    }
}
