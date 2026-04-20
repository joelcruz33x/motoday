package com.example.motoday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.motoday.ui.components.BottomNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChroniclesScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Muro de Crónicas") })
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text("Historias y álbumes de rutas compartidas por la comunidad.")
        }
    }
}
