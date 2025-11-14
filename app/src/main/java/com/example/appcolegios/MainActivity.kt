package com.example.appcolegios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.appcolegios.navigation.AppNavigation
import com.example.appcolegios.navigation.AppRoutes
import com.example.appcolegios.ui.theme.AppColegiosTheme
import com.example.appcolegios.data.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkHost = intent?.data?.host
        // Prioridad: startDestination explícito en intent (deep link o notificación)
        var startDestination: String? = intent.getStringExtra("startDestination")
        if (startDestination.isNullOrBlank()) {
            startDestination = if (deepLinkHost == "notifications") AppRoutes.Notifications.route else null
        }

        // Si no hay destino explícito, intentar leer rol desde preferencias y redirigir
        if (startDestination.isNullOrBlank()) {
            try {
                val userPrefs = UserPreferencesRepository(applicationContext)
                val userData = runBlocking { userPrefs.userData.first() }
                val role = userData.role?.uppercase()
                startDestination = when (role) {
                    "ADMIN" -> AppRoutes.Home.route // admin verá Home con tarjeta de administración
                    "DOCENTE" -> AppRoutes.TeacherHome.route
                    "PADRE" -> AppRoutes.Home.route
                    "ESTUDIANTE" -> AppRoutes.StudentHome.route
                    else -> AppRoutes.Splash.route
                }
            } catch (e: Exception) {
                startDestination = AppRoutes.Splash.route
            }
        }
        // Asegurar valor no nulo
        val resolvedStart = startDestination ?: AppRoutes.Splash.route

        setContent {
            // Crear repo aquí para usar en Compose
            val userPrefs = UserPreferencesRepository(applicationContext)
            // Observar preferencias (modo oscuro y tamaño de fuente) para aplicar tema en tiempo real
            val darkModeEnabled by userPrefs.darkModeEnabled.collectAsState(initial = false)
            val fontSizeEnum by userPrefs.fontSizeEnum.collectAsState(initial = 1)

            AppColegiosTheme(darkTheme = darkModeEnabled, fontSizeEnum = fontSizeEnum) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = resolvedStart)
                }
            }
        }
    }
}