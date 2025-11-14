@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.appcolegios.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Pantalla que reemplaza al antiguo AssignGroupDialog.
 * Permite seleccionar arriba si se trabaja con 'Docente' o 'Estudiante' y realizar las mismas acciones.
 */
@Composable
fun AssignGroupAdminScreen(navController: NavController? = null, initialIdentifier: String? = null, initialTargetType: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var identifier by remember { mutableStateOf(initialIdentifier ?: "") }
    var curso by remember { mutableStateOf("") }
    var grupo by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }

    var targetType by remember { mutableStateOf(initialTargetType ?: "teacher") } // "teacher" or "student"
    val tabTitles = listOf("Docente", "Estudiante")
    val selectedTabIndex = if (targetType == "teacher") 0 else 1

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                dialogLoading = true
                dialogMessage = null
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val reader = BufferedReader(InputStreamReader(input))
                        var line: String?
                        var processed = 0
                        var assigned = 0
                        while (true) {
                            line = reader.readLine() ?: break
                            val email = line.trim().takeIf { it.contains("@") }
                            if (email != null) {
                                processed++
                                if (targetType == "teacher") {
                                    // Buscar uid por email/uid y actualizar users/{uid} (NO crear nuevos usuarios)
                                    try {
                                        val emailLower = email.trim().lowercase()
                                        var uidFound: String? = null
                                        val uq = db.collection("users").whereEqualTo("email", emailLower).get().await()
                                        if (!uq.isEmpty) uidFound = uq.documents[0].id
                                        if (uidFound == null) {
                                            val tq = db.collection("teachers").whereEqualTo("email", emailLower).get().await()
                                            if (!tq.isEmpty) uidFound = tq.documents[0].id
                                        }
                                        if (uidFound != null) {
                                            val groupKey = if (curso.isNotBlank() && grupo.isNotBlank()) (curso.trim() + "-" + grupo.trim()).lowercase() else ""
                                            val userUpdate = mutableMapOf<String, Any?>()
                                            userUpdate["role"] = "DOCENTE"
                                            if (groupKey.isNotBlank()) {
                                                userUpdate["groupKey"] = groupKey
                                                userUpdate["curso"] = groupKey
                                                userUpdate["curso_simple"] = curso.trim()
                                                userUpdate["grupo"] = grupo.trim()
                                                userUpdate["grupos"] = FieldValue.arrayUnion(groupKey)
                                            }
                                            try { db.collection("users").document(uidFound).set(userUpdate, SetOptions.merge()).await() } catch (_: Exception) {}
                                            // también actualizar teachers/{uid} si existe
                                            try { db.collection("teachers").document(uidFound).set(userUpdate, SetOptions.merge()).await() } catch (_: Exception) {}
                                            assigned++
                                        }
                                    } catch (_: Exception) {}
                                } else {
                                    // student import: buscar uid por email, crear si no existe, y actualizar students/{uid} y users/{uid}
                                    try {
                                        val emailLower = email.trim().lowercase()
                                        var uidFound: String? = null
                                        val uq = db.collection("users").whereEqualTo("email", emailLower).get().await()
                                        if (!uq.isEmpty) uidFound = uq.documents[0].id
                                        if (uidFound == null) {
                                            val sq = db.collection("students").whereEqualTo("email", emailLower).get().await()
                                            if (!sq.isEmpty) uidFound = sq.documents[0].id
                                        }
                                        if (uidFound == null) {
                                            // No crear usuarios nuevos aquí; saltar
                                            continue
                                        }
                                        val uid = uidFound
                                        val normalizedGroup = if (curso.isNotBlank() && grupo.isNotBlank()) (curso.trim() + "-" + grupo.trim()).lowercase() else ""
                                        if (normalizedGroup.isNotBlank()) {
                                            val studentUpdate = mutableMapOf<String, Any?>(
                                                "groupKey" to normalizedGroup,
                                                "curso" to normalizedGroup,
                                                "curso_simple" to curso.trim(),
                                                "grupo" to grupo.trim(),
                                                "assignedAt" to com.google.firebase.Timestamp.now()
                                            )
                                            studentUpdate["grupos"] = FieldValue.arrayUnion(normalizedGroup)
                                            try { db.collection("students").document(uid).set(studentUpdate, SetOptions.merge()).await() } catch (_: Exception) {}

                                            val userUpdate = mutableMapOf<String, Any?>(
                                                "role" to "ESTUDIANTE",
                                                "groupKey" to normalizedGroup,
                                                "curso" to normalizedGroup,
                                                "curso_simple" to curso.trim(),
                                                "grupo" to grupo.trim()
                                            )
                                            userUpdate["grupos"] = FieldValue.arrayUnion(normalizedGroup)
                                            try { db.collection("users").document(uid).set(userUpdate, SetOptions.merge()).await() } catch (_: Exception) {}

                                            // También crear/actualizar documento en Grades/{groupKey}/students/{uid}
                                            try {
                                                val gradeDoc = db.collection("Grades").document(normalizedGroup)
                                                val studentEntry = mapOf(
                                                    "uid" to uid,
                                                    "email" to emailLower,
                                                    "assignedAt" to com.google.firebase.Timestamp.now()
                                                )
                                                // Guardar como subdocumento para facilitar consultas por grupo y por uid
                                                gradeDoc.collection("students").document(uid).set(studentEntry).await()
                                            } catch (_: Exception) {
                                                // ignorar fallo en creación de Grades
                                            }

                                            assigned++
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                        dialogMessage = "Procesados: $processed, asignados: $assigned"
                    }
                } catch (e: Exception) {
                    dialogMessage = "Error procesando archivo: ${e.message}"
                } finally {
                    dialogLoading = false
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                dialogLoading = true
                try {
                    val docs = db.collection("teachers").get().await()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os).use { writer ->
                            writer.append("uid,email,nombre\n")
                            for (d in docs.documents) {
                                val uid = d.id
                                val email = d.getString("email") ?: d.getString("correo") ?: ""
                                val nombre = d.getString("nombre") ?: d.getString("displayName") ?: ""
                                writer.append("${uid},${email},${nombre}\n")
                            }
                            writer.flush()
                        }
                    }
                    dialogMessage = "Exportado correctamente"
                } catch (e: Exception) {
                    dialogMessage = "Error exportando: ${e.message}"
                } finally {
                    dialogLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Asignar / Quitar grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { targetType = if (index == 0) "teacher" else "student" },
                        text = { Text(title) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (targetType == "teacher") {
                    // --- VISTA DOCENTE ---
                    Text("Asignar grupo a Docente", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("Email o UID del docente") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = curso,
                        onValueChange = { curso = it },
                        label = { Text("Curso (ej: 10A, 11B)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = grupo,
                        onValueChange = { grupo = it },
                        label = { Text("Grupo (ej: A, B)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            dialogLoading = true
                            val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                            if (uid != null) {
                                val groupKey = if (curso.isNotBlank() && grupo.isNotBlank()) (curso.trim() + "-" + grupo.trim()).lowercase() else ""
                                val updateData = mutableMapOf<String, Any>("role" to "DOCENTE")
                                if (groupKey.isNotBlank()) {
                                    updateData["groupKey"] = groupKey
                                    updateData["curso"] = curso.trim()
                                    updateData["grupo"] = grupo.trim()
                                    updateData["grupos"] = FieldValue.arrayUnion(groupKey)
                                }
                                try {
                                    db.collection("users").document(uid).set(updateData, SetOptions.merge()).await()
                                    db.collection("teachers").document(uid).set(updateData, SetOptions.merge()).await()
                                    dialogMessage = "Docente asignado correctamente."
                                } catch (e: Exception) {
                                    dialogMessage = "Error al asignar: ${e.message}"
                                }
                            } else {
                                dialogMessage = "Usuario no encontrado."
                            }
                            dialogLoading = false
                        }
                    }) { Text("Asignar a Docente") }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("Asignación masiva desde CSV", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    Button(onClick = { filePicker.launch(arrayOf("text/csv", "text/plain")) }) {
                        Text("Importar CSV de Docentes")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { exportLauncher.launch("docentes.csv") }) {
                        Text("Exportar plantilla Docentes")
                    }

                } else {
                    // --- VISTA ESTUDIANTE ---
                    Text("Asignar grupo a Estudiante", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("Email o UID del estudiante") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = curso,
                        onValueChange = { curso = it },
                        label = { Text("Nombre del Curso (ej: 10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = grupo,
                        onValueChange = { grupo = it },
                        label = { Text("Nombre del Grupo (ej: A)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            if (identifier.isBlank() || curso.isBlank() || grupo.isBlank()) {
                                dialogMessage = "Completa todos los campos."
                                return@launch
                            }
                            dialogLoading = true
                            val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                            if (uid != null) {
                                val groupKey = "${curso.trim()}-${grupo.trim()}".lowercase()
                                val studentUpdate = mapOf(
                                    "curso" to curso.trim(),
                                    "grupo" to grupo.trim(),
                                    "groupKey" to groupKey,
                                    "grupos" to FieldValue.arrayUnion(groupKey)
                                )
                                val userUpdate = mapOf(
                                    "role" to "ESTUDIANTE",
                                    "curso" to curso.trim(),
                                    "grupo" to grupo.trim(),
                                    "groupKey" to groupKey,
                                    "grupos" to FieldValue.arrayUnion(groupKey)
                                )
                                try {
                                    db.collection("students").document(uid).set(studentUpdate, SetOptions.merge()).await()
                                    db.collection("users").document(uid).set(userUpdate, SetOptions.merge()).await()
                                    val groupDocRef = db.collection("groups").document(groupKey)
                                    groupDocRef.set(mapOf("name" to "${curso.trim()} - ${grupo.trim()}", "curso" to curso.trim(), "grupo" to grupo.trim()), SetOptions.merge()).await()
                                    dialogMessage = "Estudiante asignado al grupo '$groupKey' correctamente."
                                } catch (e: Exception) {
                                    dialogMessage = "Error al asignar: ${e.message}"
                                }
                            } else {
                                dialogMessage = "Estudiante no encontrado con el identificador '$identifier'."
                            }
                            dialogLoading = false
                        }
                    }) { Text("Asignar a Estudiante") }
                }

                if (dialogLoading) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }

                dialogMessage?.let {
                    AlertDialog(
                        onDismissRequest = { dialogMessage = null },
                        title = { Text("Información") },
                        text = { Text(it) },
                        confirmButton = {
                            Button(onClick = { dialogMessage = null }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}
