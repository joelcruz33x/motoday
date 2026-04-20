package com.example.motoday.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.motoday.ui.screens.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = Screen.Home.route) {
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
