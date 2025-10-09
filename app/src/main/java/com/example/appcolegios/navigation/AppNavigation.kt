package com.example.appcolegios.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appcolegios.academico.AttendanceScreen
import com.example.appcolegios.academico.CalendarScreen
import com.example.appcolegios.academico.NotesScreen
import com.example.appcolegios.academico.TasksScreen
import com.example.appcolegios.auth.LoginScreen
import com.example.appcolegios.auth.RegisterScreen
import com.example.appcolegios.auth.ResetPasswordScreen
import com.example.appcolegios.auth.SplashScreen
import com.example.appcolegios.home.HomeScreen
import com.example.appcolegios.mensajes.ConversationsScreen
import com.example.appcolegios.notificaciones.NotificationsScreen
import com.example.appcolegios.pagos.PaymentsScreen
import com.example.appcolegios.perfil.ProfileScreen
import com.example.appcolegios.transporte.TransportScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppRoutes.Splash.route) {
        composable(AppRoutes.Splash.route) { SplashScreen(navController) }
        composable(AppRoutes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppRoutes.Home.route) {
                        // Limpia el backstack para que el usuario no pueda volver a Login
                        popUpTo(AppRoutes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(AppRoutes.Register.route) }
            )
        }
        composable(AppRoutes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    // Navega a Login y muestra un mensaje (implementación del mensaje más adelante)
                    navController.navigate(AppRoutes.Login.route) {
                        popUpTo(AppRoutes.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.ResetPassword.route) {
            ResetPasswordScreen(
                onPasswordResetSent = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.Home.route) { HomeScreen(navController = navController) }
        composable(AppRoutes.Profile.route) { ProfileScreen() }
        composable(AppRoutes.Payments.route) { PaymentsScreen() }
        composable(AppRoutes.Transport.route) { TransportScreen() }
        composable(AppRoutes.Notes.route) { NotesScreen() }
        composable(AppRoutes.Attendance.route) { AttendanceScreen() }
        composable(AppRoutes.Tasks.route) { TasksScreen() }
        composable(AppRoutes.Notifications.route) { NotificationsScreen() }
        composable(AppRoutes.Messages.route) { ConversationsScreen(navController = navController) }
        composable(AppRoutes.Calendar.route) { CalendarScreen() }
    }
}
