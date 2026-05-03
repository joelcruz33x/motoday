package com.example.motoday.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        
        val items = listOf(
            NavigationItem(Screen.Home.route, Icons.Default.Home, "Inicio", homeNotifications),
            NavigationItem(Screen.Explore.route, Icons.Default.Map, "Rutas", exploreNotifications),
            NavigationItem(Screen.Groups.route, Icons.Default.Groups, "Motoclubs", groupsNotifications),
            NavigationItem(Screen.Profile.route, Icons.Default.Person, "Perfil", 0)
        )

        items.forEach { item ->
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
                selected = currentRoute == item.route,
                onClick = { 
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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
