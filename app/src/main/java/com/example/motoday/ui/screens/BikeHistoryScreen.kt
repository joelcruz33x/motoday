package com.example.motoday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeHistoryScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Hoja de Vida de la Moto") })
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text("Registro de mantenimientos, kilometraje y documentos de tu moto.")
        }
    }
}
