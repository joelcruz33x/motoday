package com.example.motoday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Grupos Moteros") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Crear Grupo */ }) {
                Icon(Icons.Default.Add, contentDescription = "Crear Grupo")
            }
        },
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Discord-style sidebar for groups
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(5) {
                    GroupIconCircle()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            // Channel/Group Content
            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(text = "# General - Los Halcones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                items(10) { index ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Miembro_$index", fontWeight = FontWeight.Bold)
                            Text(text = "Este es un mensaje dentro del grupo tipo Discord. ¿Quién sale a rodar?")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupIconCircle() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
    }
}
