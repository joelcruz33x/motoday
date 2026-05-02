package com.example.motoday.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Register : Screen("register")
    object Explore : Screen("explore")
    object Profile : Screen("profile?userId={userId}") {
        fun createRoute(userId: String? = null) = if (userId != null) "profile?userId=$userId" else "profile"
    }
    
    // Sub-screens or modal screens
    object SOS : Screen("sos")
    object CreateRide : Screen("create_ride")
    object BikeDetail : Screen("bike_detail")
    object Passport : Screen("passport")
    object CreatePost : Screen("create_post")
    object Maintenance : Screen("maintenance")
    object Settings : Screen("settings")
    object Store : Screen("store")
    object Groups : Screen("groups?sharedText={sharedText}") {
        fun createRoute(sharedText: String? = null) = if (sharedText != null) "groups?sharedText=$sharedText" else "groups"
    }
    object GroupSettings : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: String) = "group_settings/$groupId"
    }
    object PrivateChat : Screen("private_chat/{userId}") {
        fun createRoute(userId: String) = "private_chat/$userId"
    }
    object PrivateChatsList : Screen("private_chats_list")
    object RideDetail : Screen("ride_detail/{rideId}") {
        fun createRoute(rideId: Int) = "ride_detail/$rideId"
    }
}
