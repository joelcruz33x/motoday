package com.example.motoday.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.motoday.data.remote.AuthManager
import com.example.motoday.ui.screens.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    
    // Estado para el destino inicial (null mientras verificamos)
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    val prefs = context.getSharedPreferences("motoday_prefs", android.content.Context.MODE_PRIVATE)
    val hasSeenWelcome = prefs.getBoolean("has_seen_welcome", false)
    
    // Verificación de sesión al arrancar
    LaunchedEffect(Unit) {
        val isLoggedIn = authManager.isUserLoggedIn()
        
        startDestination = when {
            isLoggedIn -> Screen.Home.route
            !hasSeenWelcome -> Screen.Welcome.route
            else -> Screen.Login.route
        }
    }

    // Mostramos una pantalla de carga mientras verificamos la sesión
    if (startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Una vez que sabemos a dónde ir, cargamos el NavHost
        NavHost(navController = navController, startDestination = startDestination!!) {
            composable(Screen.Welcome.route) {
                WelcomeScreen(navController)
            }
            composable(Screen.Login.route) {
                LoginScreen(navController)
            }
            composable(Screen.Register.route) {
                RegisterScreen(navController)
            }
            composable(Screen.Home.route) {
                HomeScreen(navController)
            }
            composable(Screen.Explore.route) {
                ExploreScreen(navController)
            }
            composable(Screen.Groups.route) {
                GroupsScreen(navController)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(navController)
            }
            composable(Screen.SOS.route) {
                SOSScreen(navController)
            }
            composable(Screen.CreateRide.route) {
                CreateRideScreen(navController)
            }
            composable(Screen.CreatePost.route) {
                CreatePostScreen(navController)
            }
            composable(Screen.Maintenance.route) {
                MaintenanceScreen(navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController)
            }
            composable(
                route = Screen.RideDetail.route,
                arguments = listOf(navArgument("rideId") { type = NavType.IntType })
            ) { backStackEntry ->
                val rideId = backStackEntry.arguments?.getInt("rideId") ?: 0
                RideDetailScreen(navController, rideId)
            }
        }
    }
}
