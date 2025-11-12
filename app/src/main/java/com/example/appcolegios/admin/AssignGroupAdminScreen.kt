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
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }

    var targetType by remember { mutableStateOf(initialTargetType ?: "teacher") } // "teacher" or "student"

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
                                            try { db.collection("users").document(uidFound).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                            // también actualizar teachers/{uid} si existe
                                            try { db.collection("teachers").document(uidFound).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
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
                                            try { db.collection("students").document(uid).set(studentUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}

                                            val userUpdate = mutableMapOf<String, Any?>(
                                                "role" to "ESTUDIANTE",
                                                "groupKey" to normalizedGroup,
                                                "curso" to normalizedGroup,
                                                "curso_simple" to curso.trim(),
                                                "grupo" to grupo.trim()
                                            )
                                            userUpdate["grupos"] = FieldValue.arrayUnion(normalizedGroup)
                                            try { db.collection("users").document(uid).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selector Docente / Estudiante
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { targetType = "teacher" }, colors = ButtonDefaults.textButtonColors(contentColor = if (targetType == "teacher") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) { Text("Docente") }
                TextButton(onClick = { targetType = "student" }, colors = ButtonDefaults.textButtonColors(contentColor = if (targetType == "student") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) { Text("Estudiante") }
            }

            if (targetType == "teacher") {
                Text("Introduce el email o UID del docente. También puedes importar un CSV con un email por línea para asignación masiva.")
                OutlinedTextField(value = identifier, onValueChange = { identifier = it }, label = { Text("Email o UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = curso, onValueChange = { curso = it }, label = { Text("Curso (ej. 10)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = grupo, onValueChange = { grupo = it }, label = { Text("Grupo (ej. A)") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { filePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) }, modifier = Modifier.weight(1f)) { Text("Importar CSV y asignar") }
                    OutlinedButton(onClick = { exportLauncher.launch("docentes_export.csv") }, modifier = Modifier.weight(1f)) { Text("Exportar docentes (CSV)") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@OutlinedButton }
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                                if (uid == null) {
                                    dialogMessage = "No se encontró docente/usuario con ese email/uid"
                                } else {
                                    foundUid = uid
                                    val tDoc = try { db.collection("teachers").document(uid).get().await() } catch (_: Exception) { null }
                                    val uDoc = try { db.collection("users").document(uid).get().await() } catch (_: Exception) { null }
                                    foundName = try {
                                        tDoc?.getString("nombre") ?: tDoc?.getString("name") ?: uDoc?.getString("nombre") ?: uDoc?.getString("name") ?: uDoc?.getString("displayName")
                                    } catch (_: Exception) { null }
                                    dialogMessage = "Encontrado uid=$uid"
                                }
                            } catch (e: Exception) {
                                dialogMessage = "Error búsqueda: ${e.message}"
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Buscar") }

                    TextButton(onClick = { curso = ""; grupo = ""; dialogMessage = "Campos curso/grupo limpiados" }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                }

                if (!foundName.isNullOrBlank()) Text("Docente: ${foundName} (uid=${foundUid})")
                if (!dialogMessage.isNullOrBlank()) Text(dialogMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dialogLoading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@Button }
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                // Buscar uid por email o id y actualizar users/{uid} y teachers/{uid} (no crear nuevos usuarios)
                                val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                                if (uid == null) {
                                    dialogMessage = "No se encontró usuario/docente con ese email/uid"
                                } else {
                                    val groupKey = if (curso.isNotBlank() && grupo.isNotBlank()) ("${curso.trim()}-${grupo.trim()}").lowercase() else ""
                                    val userUpdate = mutableMapOf<String, Any?>()
                                    userUpdate["role"] = "DOCENTE"
                                    if (groupKey.isNotBlank()) {
                                        userUpdate["groupKey"] = groupKey
                                        userUpdate["curso"] = groupKey
                                        userUpdate["curso_simple"] = curso.trim()
                                        userUpdate["grupo"] = grupo.trim()
                                        userUpdate["grupos"] = FieldValue.arrayUnion(groupKey)
                                    }
                                    try { db.collection("users").document(uid).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                    try { db.collection("teachers").document(uid).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                    dialogMessage = "Asignación completada y perfil actualizado (uid=$uid)"
                                }
                            } catch (e: Exception) {
                                dialogMessage = "Error asignando: ${e.message}"
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Asignar y actualizar perfil") }

                    OutlinedButton(onClick = {
                        if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@OutlinedButton }
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                                if (uid == null) {
                                    dialogMessage = "No se encontró usuario/docente"
                                } else {
                                    val groupKeyLocal = if (curso.isBlank() || grupo.isBlank()) null else ("${curso.trim()}-${grupo.trim()}")
                                    if (groupKeyLocal != null) {
                                        db.collection("teachers").document(uid).update(mapOf("grupos" to FieldValue.arrayRemove(groupKeyLocal))).await()
                                        try { db.collection("users").document(uid).update(mapOf("grupos" to FieldValue.arrayRemove(groupKeyLocal))).await() } catch (_: Exception) {}
                                        val doc = db.collection("teachers").document(uid).get().await()
                                        val currentCurso = doc.getString("curso")
                                        if (currentCurso == groupKeyLocal) {
                                            db.collection("teachers").document(uid).update(mapOf("curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await()
                                            try { db.collection("users").document(uid).update(mapOf("curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await() } catch (_: Exception) {}
                                        }
                                        dialogMessage = "Grupo $groupKeyLocal eliminado del perfil"
                                    } else {
                                        db.collection("teachers").document(uid).update(mapOf("grupos" to FieldValue.delete(), "curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await()
                                        try { db.collection("users").document(uid).update(mapOf("grupos" to FieldValue.delete(), "curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await() } catch (_: Exception) {}
                                        dialogMessage = "Todos los grupos eliminados del perfil"
                                    }
                                 }
                            } catch (e: Exception) {
                                dialogMessage = "Error quitando: ${e.message}"
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Quitar del perfil") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { navController?.popBackStack() }) { Text("Cerrar") }
                }

            } else {
                // Student flow: reuse code from dialog for assigning students
                Text("Introduce el email o UID del estudiante y el curso/grupo a asignar.")
                OutlinedTextField(value = identifier, onValueChange = { identifier = it }, label = { Text("Email o UID del estudiante") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = curso, onValueChange = { curso = it }, label = { Text("Curso (ej. 10)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = grupo, onValueChange = { grupo = it }, label = { Text("Grupo (ej. A)") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@Button }
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                // Buscar uid por email o id (no crear nuevos usuarios)
                                val uid = AdminHelpers.findUidByIdentifier(db, identifier)
                                val providedEmail = identifier.takeIf { it.contains("@") }?.trim()?.lowercase()
                                if (uid == null) {
                                    dialogMessage = "No se encontró usuario con ese email/uid; no se crearán usuarios automáticamente."
                                    return@launch
                                }

                                // Normalizar clave de grupo y actualizar directamente el documento students/{uid}
                                val normalizedGroup = (curso.trim() + "-" + grupo.trim()).lowercase()
                                val studentUpdate = hashMapOf<String, Any?>(
                                    "groupKey" to normalizedGroup,
                                    "curso_simple" to curso.trim(),
                                    "grupo" to grupo.trim(),
                                    "curso" to normalizedGroup,
                                    "assignedAt" to com.google.firebase.Timestamp.now()
                                )

                                try {
                                    // Usar SetOptions.merge() crea/actualiza el documento students/{uid} sin crear usuarios nuevos
                                    val base = studentUpdate.toMutableMap()
                                    if (!providedEmail.isNullOrBlank()) base["email"] = providedEmail
                                    try { db.collection("students").document(uid).set(base, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                } catch (e: Exception) {
                                    // ignorar
                                }

                                try {
                                    val userUpdate = mutableMapOf<String, Any?>(
                                        "role" to "ESTUDIANTE",
                                        "groupKey" to normalizedGroup,
                                        "curso" to normalizedGroup,
                                        "curso_simple" to curso.trim(),
                                        "grupo" to grupo.trim()
                                    )
                                    // añadir el array de grupos
                                    userUpdate["grupos"] = FieldValue.arrayUnion(normalizedGroup)
                                    // Actualizar users/{uid} con merge (no crea usuario nuevo si no existe el documento)
                                    db.collection("users").document(uid).set(userUpdate, com.google.firebase.firestore.SetOptions.merge()).await()
                                } catch (_: Exception) {
                                    // ignorar
                                }

                                // También crear/actualizar documento en Grades/{groupKey}/students/{uid}
                                try {
                                    val emailToStore = providedEmail ?: try { db.collection("users").document(uid).get().await().getString("email") ?: "" } catch (_: Exception) { "" }
                                    if (normalizedGroup.isNotBlank()) {
                                        val gradeDoc = db.collection("Grades").document(normalizedGroup)
                                        val studentEntry = mapOf(
                                            "uid" to uid,
                                            "email" to emailToStore,
                                            "assignedAt" to com.google.firebase.Timestamp.now()
                                        )
                                        gradeDoc.collection("students").document(uid).set(studentEntry).await()
                                    }
                                } catch (_: Exception) {
                                    // ignorar fallo en creación de Grades
                                }

                                dialogMessage = "Estudiante asignado al grupo (uid=$uid)"
                            } catch (e: Exception) {
                                dialogMessage = "Error asignando estudiante: ${e.message}"
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Asignar estudiante") }

                    OutlinedButton(onClick = { identifier = ""; curso = ""; grupo = ""; dialogMessage = "Campos limpiados" }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                }

                // Botón administrativo para rellenar estudiantes que no tienen 'curso' (backfill)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                var updated = 0
                                val all = db.collection("students").get().await()
                                for (doc in all.documents) {
                                    try {
                                        val id = doc.id
                                        val existingCurso = doc.getString("curso")
                                        if (existingCurso.isNullOrBlank()) {
                                            var groupKey = doc.getString("groupKey") ?: ""
                                            if (groupKey.isBlank()) {
                                                // intentar desde users/{id}
                                                try {
                                                    val u = db.collection("users").document(id).get().await()
                                                    if (u.exists()) {
                                                        groupKey = u.getString("groupKey") ?: u.getString("curso") ?: ""
                                                    }
                                                } catch (_: Exception) { }
                                            }

                                            if (groupKey.isNotBlank()) {
                                                val parts = groupKey.split("-")
                                                val cursoSimple = parts.getOrNull(0)?.trim() ?: ""
                                                val grupoVal = parts.getOrNull(1)?.trim() ?: ""
                                                val sUpd = mutableMapOf<String, Any?>(
                                                    "curso" to groupKey,
                                                    "curso_simple" to cursoSimple,
                                                    "grupo" to grupoVal
                                                )
                                                sUpd["grupos"] = FieldValue.arrayUnion(groupKey)
                                                try { db.collection("students").document(id).set(sUpd, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                                try { db.collection("users").document(id).set(sUpd, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
                                                updated++
                                            }
                                        }
                                    } catch (_: Exception) { }
                                }
                                dialogMessage = "Backfill completado: $updated estudiantes actualizados"
                            } catch (e: Exception) {
                                dialogMessage = "Error backfill: ${e.message}"
                            } finally {
                                dialogLoading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar estudiantes (backfill)") }
                }

                if (!dialogMessage.isNullOrBlank()) Text(dialogMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dialogLoading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { navController?.popBackStack() }) { Text("Cerrar") }
                }
            }
        }
    }
}
