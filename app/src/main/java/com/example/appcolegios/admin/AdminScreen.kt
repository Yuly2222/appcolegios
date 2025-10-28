@file:Suppress("RedundantQualifierName", "RemoveRedundantQualifierName", "RedundantQualifiedName", "RedundantQualifier", "UNUSED", "unused")

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
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.tasks.await
import com.example.appcolegios.data.UserData
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.example.appcolegios.auth.RegisterActivity
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedInputStream
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.apache.poi.ss.usermodel.Row
import com.example.appcolegios.data.TestDataInitializer

// Helper local para leer celdas de forma segura
private fun safeCellValue(row: Row, index: Int): String? = row.getCell(index)?.toString()?.trim()?.takeIf { it.isNotBlank() }

@Composable
fun AdminScreen() {
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

                // Intentar inicializar FirebaseApp secundaria (importer) si existe google-services
                var authImporter: FirebaseAuth? = null
                try {
                    val opts = FirebaseOptions.fromResource(context)
                    val importerApp = if (opts != null) {
                        try { FirebaseApp.getInstance("importer") } catch (_: IllegalStateException) { FirebaseApp.initializeApp(context, opts, "importer") }
                    } else null
                    authImporter = if (importerApp != null) FirebaseAuth.getInstance(importerApp) else null
                } catch (_: Exception) { authImporter = null }

                try {
                    // Detectar por extensión si es CSV o XLSX
                    val name = uri.lastPathSegment ?: ""
                    val isXlsx = name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xls", ignoreCase = true)
                    var count = 0
                    if (isXlsx) {
                        // Leer con Apache POI
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BufferedInputStream(input).use { bis ->
                                val wb = WorkbookFactory.create(bis)
                                val sheet = wb.getSheetAt(0)
                                for (r in 1..sheet.lastRowNum) {
                                    val row = sheet.getRow(r) ?: continue
                                    // Soportar dos formatos:
                                    // Formato completo (9 cols): 0 nombre,1 apellidos,2 tipoDoc,3 numeroDoc,4 celular,5 direccion,6 email,7 pass,8 rol
                                    // Formato compacto (>=5 cols): 0 nombre,1 apellidos,2 tipo,3 email,4 role
                                    val cellCount = row.lastCellNum?.toInt() ?: 0
                                    var nombre = ""
                                    var apellidos = ""
                                    var tipo = ""
                                    var email = ""
                                    var pass: String? = null
                                    var rol = "ESTUDIANTE"

                                    if (cellCount >= 9) {
                                        nombre = row.getCell(0)?.toString()?.trim() ?: ""
                                        @Suppress("RemoveRedundantQualifierName")
                                        apellidos = safeCellValue(row, 1) ?: ""
                                        @Suppress("RemoveRedundantQualifierName")
                                        tipo = safeCellValue(row, 2) ?: ""
                                        // salto campos intermedios
                                        email = safeCellValue(row, 6) ?: ""
                                        val cell7 = safeCellValue(row,7)
                                        pass = cell7
                                        // leer rol
                                        val rawRol = safeCellValue(row,8) ?: ""
                                        rol = if (rawRol.isBlank()) "ESTUDIANTE" else rawRol
                                    } else if (cellCount >= 5) {
                                        nombre = safeCellValue(row, 0) ?: row.getCell(0)?.toString()?.trim() ?: ""
                                        @Suppress("RemoveRedundantQualifierName")
                                        apellidos = safeCellValue(row, 1) ?: ""
                                        @Suppress("RemoveRedundantQualifierName")
                                        tipo = safeCellValue(row, 2) ?: ""
                                        email = safeCellValue(row, 3) ?: ""
                                        rol = safeCellValue(row, 4) ?: "ESTUDIANTE"
                                        // si hay columna 5 posible password
                                        if (cellCount > 5) pass = safeCellValue(row, 5)
                                    } else {
                                        // intentar una heuristica: buscar el primer email-like cell
                                        for (c in 0 until cellCount) {
                                            val v = row.getCell(c)?.toString()?.trim() ?: ""
                                            if (v.contains("@")) { email = v; break }
                                        }
                                        nombre = row.getCell(0)?.toString()?.trim() ?: ""
                                    }

                                    if (email.isBlank() || nombre.isBlank()) continue

                                    val roleParsed = when (rol.uppercase()) {
                                        "PADRE", "PARENT" -> "PADRE"
                                        "DOCENTE", "TEACHER" -> "DOCENTE"
                                        "ADMIN", "ADMINISTRADOR" -> "ADMIN"
                                        else -> "ESTUDIANTE"
                                    }

                                    val coll = when (roleParsed) {
                                        "PADRE" -> "parents"
                                        "DOCENTE" -> "teachers"
                                        "ADMIN" -> "admins"
                                        else -> "students"
                                    }

                                    try {
                                        if (pass != null && authImporter != null) {
                                            // Crear en Auth y guardar con UID
                                            val res = authImporter.createUserWithEmailAndPassword(email, pass).await()
                                            val uid = res.user?.uid ?: db.collection(coll).document().id
                                            val data = hashMapOf(
                                                "nombres" to nombre,
                                                "apellidos" to apellidos,
                                                "email" to email,
                                                "tipo" to tipo,
                                                "role" to roleParsed,
                                                "importedAt" to Timestamp.now()
                                            )
                                            db.collection(coll).document(uid).set(data).await()
                                            // Además, crear documento en la colección "users" para que la app lo encuentre
                                            try {
                                                val displayName = ("$nombre $apellidos").trim()
                                                val userMap = hashMapOf(
                                                    "displayName" to displayName,
                                                    "email" to email,
                                                    "role" to roleParsed
                                                )
                                                db.collection("users").document(uid).set(userMap).await()
                                            } catch (_: Exception) { }
                                            // enviar verificacion de forma asíncrona y esperar
                                            try {
                                                res.user?.let { it.sendEmailVerification().await() }
                                            } catch (_: Exception) { }
                                            // Asegurarse de no dejar al importer autenticado
                                            try { authImporter.signOut() } catch (_: Exception) {}
                                        } else {
                                            // Guardar doc y encolar petición para backend
                                            val data = hashMapOf(
                                                "nombres" to nombre,
                                                "apellidos" to apellidos,
                                                "email" to email,
                                                "tipo" to tipo,
                                                "role" to roleParsed,
                                                "importedAt" to Timestamp.now()
                                            )
                                            db.collection(coll).add(data).await()
                                            db.collection("auth_queue").add(hashMapOf(
                                                "email" to email,
                                                "role" to roleParsed,
                                                "displayName" to ("$nombre $apellidos").trim(),
                                                "requestedAt" to Timestamp.now()
                                            )).await()

                                            // Cambio mínimo: crear también un documento en "users" para que la app tenga acceso al correo
                                            try {
                                                val userMap = hashMapOf(
                                                    "displayName" to ("$nombre $apellidos").trim(),
                                                    "email" to email,
                                                    "role" to roleParsed,
                                                    "importedAt" to Timestamp.now()
                                                )
                                                db.collection("users").add(userMap).await()
                                            } catch (_: Exception) { }

                                            // Intentar enviar email de restablecimiento (si la cuenta ya existe esto ayudará al usuario a establecer contraseña)
                                            try {
                                                val mainAuth = FirebaseAuth.getInstance()
                                                mainAuth.sendPasswordResetEmail(email).await()
                                            } catch (_: Exception) { }
                                        }
                                        count++
                                    } catch (e: Exception) {
                                        // registrar error y continuar
                                    }
                                }
                                wb.close()
                            }
                        }
                    } else {
                        // CSV (mantener comportamiento anterior, con intento de creación si hay password column)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val reader = BufferedReader(InputStreamReader(input))
                            reader.readLine() // descartar encabezado
                            var line: String?
                            var localCount = 0
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
                                    val maybePass = parts.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
                                    val coll = when (parsedRole) {
                                        "PADRE" -> "parents"
                                        "DOCENTE" -> "teachers"
                                        "ADMIN" -> "admins"
                                        else -> "students"
                                    }

                                    try {
                                        if (maybePass != null && authImporter != null) {
                                            val res = authImporter.createUserWithEmailAndPassword(email, maybePass).await()
                                            val uid = res.user?.uid ?: db.collection(coll).document().id
                                            db.collection(coll).document(uid).set(hashMapOf(
                                                "name" to name,
                                                "email" to email,
                                                "role" to parsedRole,
                                                "type" to type,
                                                "importedAt" to Timestamp.now()
                                            )).await()
                                        } else {
                                            db.collection(coll).add(hashMapOf(
                                                "email" to email,
                                                "name" to name,
                                                "role" to parsedRole,
                                                "type" to type,
                                                "importedAt" to Timestamp.now()
                                            )).await()
                                            db.collection("auth_queue").add(hashMapOf(
                                                "email" to email,
                                                "role" to parsedRole,
                                                "displayName" to name,
                                                "requestedAt" to Timestamp.now()
                                            )).await()
                                        }
                                        localCount++
                                    } catch (e: Exception) {
                                        // continuar
                                    }
                                }
                            }
                            count = localCount
                        }
                    }

                    status = "Se importaron $count registros correctamente."
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
                                TestDataInitializer.initializeAllTestData()
                                status = "✅ Datos de prueba inicializados correctamente"
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

        // Botón para importar desde CSV/XLSX
        Button(
            onClick = { filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/*","text/csv","text/comma-separated-values")) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importar desde CSV / Excel (.xlsx)")
        }

        Text(
            "Formato CSV: tipo,email,nombre,rol\nEjemplo: estudiante,juan@mail.com,Juan Pérez,ESTUDIANTE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
