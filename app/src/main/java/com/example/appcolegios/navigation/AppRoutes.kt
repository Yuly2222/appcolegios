package com.example.appcolegios.navigation

sealed class AppRoutes(val route: String) {
    object Splash : AppRoutes("splash")
    object Login : AppRoutes("login")
    object Register : AppRoutes("register")
    object ResetPassword : AppRoutes("reset_password")
    object Home : AppRoutes("home")
    object Profile : AppRoutes("profile")
    object Payments : AppRoutes("payments")
    object Transport : AppRoutes("transport")
    object Notes : AppRoutes("notes")
    object Attendance : AppRoutes("attendance")
    object Tasks : AppRoutes("tasks")
    object Notifications : AppRoutes("notifications")
    object Messages : AppRoutes("messages")
    object Calendar : AppRoutes("calendar")
}
