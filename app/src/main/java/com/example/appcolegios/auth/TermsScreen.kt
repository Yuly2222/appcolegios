package com.example.appcolegios.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.appcolegios.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun TermsScreen(navController: NavController, userPrefs: UserPreferencesRepository) {
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F6F9)))
        )) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Términos de protección de menores", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Antes de continuar, por favor lee y acepta los términos de protección de menores.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Texto de términos (resumen profesional); se recomienda enlazar a política completa en web
            Text(text = buildString {
                append("1. Privacidad: No se compartirán fotos o datos personales de menores sin consentimiento explícito de los padres/tutores.\n\n")
                append("2. Uso de imágenes: Las fotos de perfil y cualquier imagen subidas por usuarios serán almacenadas en Firebase Storage y sólo referenciadas por URL en la base de datos.\n\n")
                append("3. Acceso y control: Sólo personal autorizado (administradores y docentes con permisos) podrá editar información académica sensible. Los padres y estudiantes pueden ver la información asociada a su cuenta.\n\n")
                append("4. Reportes y moderación: Cualquier contenido inadecuado puede ser reportado y será revisado por el equipo del colegio.\n\n")
                append("5. Derechos de los tutores: Los padres/tutores pueden solicitar eliminación o rectificación de datos personales.\n\n")
                append("6. Contacto: Para dudas relacionadas con la protección de menores y privacidad, contacta al responsable de privacidad del colegio.")
            }, style = MaterialTheme.typography.bodySmall, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    // Rechazar -> cerrar aplicación o volver a splash y no permitir continuar al login
                    // Aquí navegamos al splash con error (podríamos mostrar un dialog explicativo)
                    navController.popBackStack()
                }) {
                    Text("Rechazar")
                }

                Button(onClick = {
                    scope.launch {
                        userPrefs.setProtectionTermsAccepted(true)
                        // Navegar al login (start activity) como hace la lógica actual en Splash
                        navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                    }
                }) {
                    Text("Aceptar")
                }
            }
        }
    }
}

