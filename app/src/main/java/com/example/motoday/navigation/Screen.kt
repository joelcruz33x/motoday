package com.example.motoday.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Register : Screen("register")
    object Explore : Screen("explore")
    object Groups : Screen("groups")
    object Profile : Screen("profile")
    
    // Sub-screens or modal screens
    object SOS : Screen("sos")
    object CreateRide : Screen("create_ride")
    object BikeDetail : Screen("bike_detail")
    object Passport : Screen("passport")
    object CreatePost : Screen("create_post")
    object Maintenance : Screen("maintenance")
    object Settings : Screen("settings")
    object GroupSettings : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/$groupId"
    }
    object RideDetail : Screen("ride_detail/{rideId}") {
        fun createRoute(rideId: Int) = "ride_detail/$rideId"
    }
}
