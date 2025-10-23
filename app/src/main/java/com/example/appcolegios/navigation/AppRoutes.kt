package com.example.appcolegios.navigation

sealed class AppRoutes(val route: String) {
    object Splash : AppRoutes("splash")
    object Login : AppRoutes("login")
    object Register : AppRoutes("register")
    object RegisterAdmin : AppRoutes("register_admin")
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
    object Admin : AppRoutes("admin")
    object Dashboard : AppRoutes("dashboard")
    object Chat : AppRoutes("chat/{otherUserId}")
    object NewMessage : AppRoutes("new_message")
    object Ubicacion : AppRoutes("ubicacion")
}
