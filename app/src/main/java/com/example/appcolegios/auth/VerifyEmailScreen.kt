package com.example.appcolegios.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.example.appcolegios.data.UserPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun VerifyEmailScreen(onDone: () -> Unit, onVerified: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userPrefs = remember { UserPreferencesRepository(context) }

    var isChecking by remember { mutableStateOf(false) }
    var attemptsLeft by remember { mutableStateOf(3) }

    // Polling automático: reintentar cada 6 segundos hasta attemptsLeft veces
    LaunchedEffect(Unit) {
        if (attemptsLeft > 0) {
            isChecking = true
            while (attemptsLeft > 0) {
                try {
                    val user = auth.currentUser
                    if (user == null) break
                    user.reload().await()
                    if (user.isEmailVerified) {
                        // reutilizar la misma lógica que el botón
                        var role: String? = null
                        var displayName: String? = null
                        try {
                            val udoc = firestore.collection("users").document(user.uid).get().await()
                            if (udoc.exists()) {
                                role = udoc.getString("role")
                                displayName = udoc.getString("displayName") ?: udoc.getString("name")
                            }
                        } catch (_: Exception) {}

                        if (role.isNullOrBlank() || displayName.isNullOrBlank()) {
                            val collections = listOf("students","teachers","parents","admins")
                            for (coll in collections) {
                                try {
                                    val doc = firestore.collection(coll).document(user.uid).get().await()
                                    if (doc.exists()) {
                                        role = doc.getString("role") ?: when (coll) {
                                            "students" -> "ESTUDIANTE"
                                            "teachers" -> "DOCENTE"
                                            "parents" -> "PADRE"
                                            "admins" -> "ADMIN"
                                            else -> "ADMIN"
                                        }
                                        displayName = doc.getString("nombres") ?: doc.getString("name") ?: displayName
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (role.isNullOrBlank()) role = "ADMIN"

                        // Guardar en preferencias
                        withContext(Dispatchers.IO) {
                            try { userPrefs.updateUserData(user.uid, role, displayName) } catch (_: Exception) {}
                        }

                        val startDestination = when (role.uppercase()) {
                            "ADMIN" -> com.example.appcolegios.navigation.AppRoutes.Home.route
                            "DOCENTE" -> com.example.appcolegios.navigation.AppRoutes.TeacherHome.route
                            "PADRE" -> com.example.appcolegios.navigation.AppRoutes.Home.route
                            "ESTUDIANTE" -> com.example.appcolegios.navigation.AppRoutes.StudentHome.route
                            else -> com.example.appcolegios.navigation.AppRoutes.Home.route
                        }

                        onVerified(startDestination)
                        isChecking = false
                        break
                    }
                } catch (_: Exception) {
                    // ignore and retry
                }
                attemptsLeft -= 1
                delay(6000)
            }
            isChecking = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verifica tu correo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Te hemos enviado un correo de verificación. Por favor revisa tu bandeja y pulsa el enlace para activar tu cuenta.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            // Reenviar correo de verificación
            scope.launch {
                try {
                    val user = auth.currentUser
                    if (user != null && !user.isEmailVerified) {
                        user.sendEmailVerification()
                        Toast.makeText(context, "Correo de verificación reenviado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Usuario no disponible o ya verificado", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al reenviar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("Reenviar correo")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onDone() }) {
            Text("Volver al inicio")
        }
        Spacer(Modifier.height(12.dp))
        // Nuevo botón: comprobar manualmente si ya verificó y redirigir
        Button(onClick = {
            scope.launch {
                try {
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(context, "Usuario no disponible", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    user.reload().await()
                    if (user.isEmailVerified) {
                        // obtener role/displayName desde users o colecciones
                        var role: String? = null
                        var displayName: String? = null
                        try {
                            val udoc = firestore.collection("users").document(user.uid).get().await()
                            if (udoc.exists()) {
                                role = udoc.getString("role")
                                displayName = udoc.getString("displayName") ?: udoc.getString("name")
                            }
                        } catch (_: Exception) {}

                        if (role.isNullOrBlank() || displayName.isNullOrBlank()) {
                            val collections = listOf("students","teachers","parents","admins")
                            for (coll in collections) {
                                try {
                                    val doc = firestore.collection(coll).document(user.uid).get().await()
                                    if (doc.exists()) {
                                        role = doc.getString("role") ?: when (coll) {
                                            "students" -> "ESTUDIANTE"
                                            "teachers" -> "DOCENTE"
                                            "parents" -> "PADRE"
                                            "admins" -> "ADMIN"
                                            else -> "ADMIN"
                                        }
                                        displayName = doc.getString("nombres") ?: doc.getString("name") ?: displayName
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (role.isNullOrBlank()) role = "ADMIN"

                        // Guardar en preferencias
                        withContext(Dispatchers.IO) {
                            try { userPrefs.updateUserData(user.uid, role, displayName) } catch (_: Exception) {}
                        }

                        val startDestination = when (role.uppercase()) {
                            "ADMIN" -> com.example.appcolegios.navigation.AppRoutes.Home.route
                            "DOCENTE" -> com.example.appcolegios.navigation.AppRoutes.TeacherHome.route
                            "PADRE" -> com.example.appcolegios.navigation.AppRoutes.Home.route
                            "ESTUDIANTE" -> com.example.appcolegios.navigation.AppRoutes.StudentHome.route
                            else -> com.example.appcolegios.navigation.AppRoutes.Home.route
                        }

                        onVerified(startDestination)
                    } else {
                        Toast.makeText(context, "Aún no has verificado tu correo.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("He verificado — Continuar")
        }

        // Indicador de estado: comprobando X veces...
        if (isChecking) {
            Spacer(Modifier.height(12.dp))
            Text("Comprobando... Intentos restantes: $attemptsLeft", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
