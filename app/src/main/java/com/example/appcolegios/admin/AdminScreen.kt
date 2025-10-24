package com.example.appcolegios.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.tasks.await
import com.example.appcolegios.data.UserData
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.example.appcolegios.navigation.AppRoutes
import com.example.appcolegios.auth.RegisterActivity

@Composable
fun AdminScreen(navController: NavController) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val userData = userPrefs.userData.collectAsState(initial = UserData(null, null, null)).value
    // Tratar rol nulo o vacío como ADMIN
    val role = if (userData.role.isNullOrBlank()) Role.ADMIN else Role.fromString(userData.role)

    if (role != Role.ADMIN) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No autorizado", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                status = null
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(input))
                    var line: String?
                    var count = 0
                    // Descartar encabezado si existe
                    reader.readLine()
                    while (true) {
                        line = reader.readLine() ?: break
                        val parts = line.split(',')
                        if (parts.size >= 3) {
                            val type = parts[0].trim().lowercase()
                            val email = parts[1].trim()
                            val name = parts[2].trim()
                            val parsedRole = when (parts.getOrNull(3)?.trim()?.uppercase()) {
                                "PADRE" -> "PADRE"
                                "DOCENTE" -> "DOCENTE"
                                "ADMIN" -> "ADMIN"
                                else -> "ESTUDIANTE"
                            }
                            val data = hashMapOf(
                                "email" to email,
                                "name" to name,
                                "role" to parsedRole,
                                "type" to type,
                                "importedAt" to com.google.firebase.Timestamp.now()
                            )
                            val coll = when (parsedRole) {
                                "PADRE" -> "parents"
                                "DOCENTE" -> "teachers"
                                "ADMIN" -> "admins"
                                else -> "students"
                            }
                            db.collection(coll).add(data).await()
                            count++
                        }
                    }
                    reader.close()
                    status = "Se importaron $count registros correctamente. Nota: la creación de cuentas de autenticación masiva requiere backend (Admin SDK)."
                } catch (e: Exception) {
                    status = "Error importando: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Panel de Administración",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Nueva tarjeta: Registrar usuario
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(modifier = Modifier.weight(1f), onClick = {
                // Lanzar RegisterActivity en modo admin (no afectar sesión)
                val intent = android.content.Intent(context, RegisterActivity::class.java)
                intent.putExtra("fromAdmin", true)
                context.startActivity(intent)
            }) {
                Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Registrar usuario")
                }
            }

            // Se eliminaron las tarjetas "Accesos Admin" y "Dashboard" para dejar solo la opción de registrar usuario
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }

        if (status != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (status!!.contains("Error"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    status!!,
                    modifier = Modifier.padding(16.dp),
                    color = if (status!!.contains("Error"))
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        // Botón para inicializar datos de prueba
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Inicializar Datos de Prueba",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crea usuarios de prueba con información completa:\n" +
                    "• Estudiante: jcamilodiaz7@gmail.com\n" +
                    "• Profesor: hermanitos605@gmail.com\n" +
                    "• Admin: jcamilodiaz777@gmail.com\n" +
                    "• Padre: hermanitos604@gmail.com",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            status = null
                            try {
                                com.example.appcolegios.data.TestDataInitializer.initializeAllTestData()
                                status = "�� Datos de prueba inicializados correctamente"
                            } catch (e: Exception) {
                                status = "❌ Error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Inicializar Datos de Prueba")
                }
            }
        }

        // Botón para importar desde CSV
        Button(
            onClick = { filePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importar desde CSV")
        }

        Text(
            "Formato CSV: tipo,email,nombre,rol\nEjemplo: estudiante,juan@mail.com,Juan Pérez,ESTUDIANTE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
