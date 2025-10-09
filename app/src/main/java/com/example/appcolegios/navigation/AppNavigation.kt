package com.example.appcolegios.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appcolegios.academico.AttendanceScreen
import com.example.appcolegios.academico.CalendarScreen
import com.example.appcolegios.academico.GradesScreen
import com.example.appcolegios.academico.HomeworkScreen
import com.example.appcolegios.auth.LoginScreen
import com.example.appcolegios.auth.RegisterScreen
import com.example.appcolegios.auth.ResetPasswordScreen
import com.example.appcolegios.auth.SplashScreen
import com.example.appcolegios.home.HomeScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.appcolegios.mensajes.ChatScreen
import com.example.appcolegios.mensajes.ConversationsScreen
import com.example.appcolegios.notificaciones.NotificationsScreen
import com.example.appcolegios.pagos.PaymentsScreen
import com.example.appcolegios.perfil.AcademicInfoScreen
import com.example.appcolegios.perfil.ProfileScreen
import com.example.appcolegios.transporte.TransportScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.UserData
import com.example.appcolegios.academico.ScheduleScreen
import com.example.appcolegios.academico.EventsScreen
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userPreferencesRepository = UserPreferencesRepository(context)
    val userData by userPreferencesRepository.userData.collectAsState(initial = UserData(null, null))
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("reset") { ResetPasswordScreen(navController) }

        composable("home") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) {
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            } else {
                HomeScreen(navController)
            }
        }
        composable("results") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) {
                    navController.navigate("login") { popUpTo("results") { inclusive = true } }
                }
            } else {
                GradesScreen()
            }
        }
        composable("attendance") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("attendance") { inclusive = true } } }
            } else {
                AttendanceScreen()
            }
        }
        composable("tasks") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("tasks") { inclusive = true } } }
            } else {
                HomeworkScreen()
            }
        }
        composable("calendar") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("calendar") { inclusive = true } } }
            } else {
                CalendarScreen()
            }
        }
        composable("calendar/attendance") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("calendar/attendance") { inclusive = true } } }
            } else {
                AttendanceScreen()
            }
        }
        composable("calendar/schedule") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("calendar/schedule") { inclusive = true } } }
            } else {
                ScheduleScreen()
            }
        }
        composable("calendar/events") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("calendar/events") { inclusive = true } } }
            } else {
                EventsScreen()
            }
        }
        composable("notifications") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("notifications") { inclusive = true } } }
            } else {
                NotificationsScreen()
            }
        }
        composable("inbox") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("inbox") { inclusive = true } } }
            } else {
                ConversationsScreen(navController)
            }
        }
        composable(
            route = "chat/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId")
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("chat/{threadId}") { inclusive = true } } }
            } else if (threadId != null) {
                ChatScreen(navController, threadId)
            }
        }
        composable("payments") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("payments") { inclusive = true } } }
            } else {
                PaymentsScreen(navController)
            }
        }
        composable("transport") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("transport") { inclusive = true } } }
            } else {
                TransportScreen()
            }
        }
        composable("profile") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("profile") { inclusive = true } } }
            } else {
                ProfileScreen(navController)
            }
        }
        composable("academics") {
            if (userData.userId == null) {
                LaunchedEffect(Unit) { navController.navigate("login") { popUpTo("academics") { inclusive = true } } }
            } else {
                AcademicInfoScreen()
            }
        }
    }
}
