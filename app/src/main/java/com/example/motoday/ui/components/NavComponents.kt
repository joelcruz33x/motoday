package com.example.motoday.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.motoday.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(
    navController: NavController,
    homeNotifications: Int = 0,
    exploreNotifications: Int = 0,
    groupsNotifications: Int = 0
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        
        val items = listOf(
            NavigationItem(Screen.Home.route, Icons.Default.Home, "Inicio", homeNotifications),
            NavigationItem(Screen.Explore.route, Icons.Default.Map, "Rutas", exploreNotifications),
            NavigationItem(Screen.Groups.route, Icons.Default.Groups, "Motoclubs", groupsNotifications),
            NavigationItem("profile", Icons.Default.Person, "Perfil", 0)
        )

        items.forEach { item ->
            // Comprobación de selección basada en jerarquía (maneja argumentos y subrutas)
            val isSelected = currentDestination?.hierarchy?.any { 
                val baseRoute = it.route?.split("?")?.firstOrNull()
                baseRoute == item.route.split("?").first() 
            } == true

            NavigationBarItem(
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.notifications > 0) {
                                Badge {
                                    Text(item.notifications.toString())
                                }
                            }
                        }
                    ) {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
                selected = isSelected,
                onClick = { 
                    navController.navigate(item.route) {
                        // Al navegar desde la barra inferior, limpiamos la pila hasta el inicio
                        // para evitar quedar "atrapados" en sub-pantallas como perfiles ajenos.
                        popUpTo(navController.graph.findStartDestination().id) {
                            // No usamos saveState para asegurar que la pestaña se reinicie
                        }
                        
                        // Evita duplicar la pantalla si ya estamos en ella
                        launchSingleTop = true
                        
                        // No usamos restoreState para forzar la recarga limpia de la sección
                    }
                }
            )
        }
    }
}

data class NavigationItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val notifications: Int = 0
)
