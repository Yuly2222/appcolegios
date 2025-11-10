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
    object Announcements : AppRoutes("announcements")
    object Tasks : AppRoutes("tasks")
    object Notifications : AppRoutes("notifications")
    object Messages : AppRoutes("messages")
    object Calendar : AppRoutes("calendar")
    object Schedule : AppRoutes("schedule")
    object Admin : AppRoutes("admin")
    object Dashboard : AppRoutes("dashboard")
    object TeacherHome : AppRoutes("teacher_home")
    object Grading : AppRoutes("grading")
    object StudentHome : AppRoutes("student_home")
    object VerifyEmail : AppRoutes("verify_email")
    object Chat : AppRoutes("chat/{otherUserId}")
    object NewMessage : AppRoutes("new_message")
    object Ubicacion : AppRoutes("ubicacion")

    // Admin helpers
    object AdminUsers : AppRoutes("admin_users/{mode}")
    object AdminScheduleManage : AppRoutes("admin_schedule/{userId}")
    object AdminEventCreate : AppRoutes("admin_event_create")
    object AdminProfileDetail : AppRoutes("admin_profile/{userId}")

    // Calendar deep link to open specific event
    object CalendarEvent : AppRoutes("calendar/{eventId}")
}
