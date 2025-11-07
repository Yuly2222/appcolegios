package com.example.appcolegios.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@Composable
fun AssignGroupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var identifier by remember { mutableStateOf("") } // email o uid
    var curso by remember { mutableStateOf("") } // e.g. 10
    var grupo by remember { mutableStateOf("") } // e.g. A
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }

    // file picker para CSV simple (email por línea)
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
                                val result = assignToCourseAndGroup(db, email, curso, grupo)
                                if (result) assigned++
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

    // Export launcher para crear un CSV en almacenamiento (Create Document)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                dialogLoading = true
                try {
                    // leer todos los teachers y escribir CSV
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

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Asignar / Quitar curso y grupo", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Introduce el email o UID del docente. También puedes importar un CSV con un email por línea para asignación masiva.")

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Email o UID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = curso, onValueChange = { curso = it }, label = { Text("Curso (ej. 10)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = grupo, onValueChange = { grupo = it }, label = { Text("Grupo (ej. A)") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { filePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) }, modifier = Modifier.weight(1f)) {
                        Text("Importar CSV y asignar")
                    }
                    OutlinedButton(onClick = { exportLauncher.launch("docentes_export.csv") }, modifier = Modifier.weight(1f)) {
                        Text("Exportar docentes (CSV)")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@OutlinedButton }
                        dialogLoading = true
                        dialogMessage = null
                        scope.launch {
                            try {
                                val uid = findUidByIdentifier(db, identifier)
                                if (uid == null) {
                                    dialogMessage = "No se encontró docente/usuario con ese email/uid"
                                } else {
                                    foundUid = uid
                                    // leer nombre desde teachers o users (soportar 'nombre' y 'name' y 'displayName')
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

                    // Acción de limpiar los campos de curso/grupo
                    TextButton(onClick = { curso = ""; grupo = ""; dialogMessage = "Campos curso/grupo limpiados" }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                }

                if (!foundName.isNullOrBlank()) Text("Docente: ${foundName} (uid=${foundUid})", style = MaterialTheme.typography.bodyMedium)
                if (!dialogMessage.isNullOrBlank()) Text(dialogMessage!!, style = MaterialTheme.typography.bodySmall)
                if (dialogLoading) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (identifier.isBlank()) { dialogMessage = "Introduce email o uid"; return@Button }
                    dialogLoading = true
                    dialogMessage = null
                    scope.launch {
                        try {
                            val success = assignToCourseAndGroup(db, identifier, curso, grupo)
                            if (success) dialogMessage = "Asignación completada y perfil actualizado"
                            else dialogMessage = "No se encontró usuario/docente; se creó un registro en teachers si fue posible."
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
                            val uid = findUidByIdentifier(db, identifier)
                            if (uid == null) {
                                dialogMessage = "No se encontró usuario/docente"
                            } else {
                                // eliminar campos curso/grupo
                                db.collection("teachers").document(uid).update(mapOf("curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await()
                                try { db.collection("users").document(uid).update(mapOf("curso" to FieldValue.delete(), "grupo" to FieldValue.delete(), "curso_simple" to FieldValue.delete())).await() } catch (_: Exception) {}
                                dialogMessage = "Curso y grupo eliminados del perfil"
                            }
                        } catch (e: Exception) {
                            dialogMessage = "Error quitando: ${e.message}"
                        } finally {
                            dialogLoading = false
                        }
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Quitar del perfil") }
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cerrar") }
        }
    )
}

// Helper: busca uid por email o uid; devuelve null si no existe
private suspend fun findUidByIdentifier(db: FirebaseFirestore, identifier: String): String? {
    // si parece email
    if (identifier.contains("@")) {
        val idNorm = identifier.trim()
        val idLower = idNorm.lowercase()
        // Buscar en varias colecciones comunes donde puede almacenarse el email
        val collectionsToCheck = listOf("teachers", "users", "admins", "students", "parents")
        for (coll in collectionsToCheck) {
            // intentar match exacto
            val q = db.collection(coll).whereEqualTo("email", idNorm).limit(1).get().await()
            if (q.documents.isNotEmpty()) return q.documents[0].id
            // intentar con campo 'correo'
            val q2 = db.collection(coll).whereEqualTo("correo", idNorm).limit(1).get().await()
            if (q2.documents.isNotEmpty()) return q2.documents[0].id
            // intentar con lowercase (por si los emails se guardaron normalizados)
            val q3 = db.collection(coll).whereEqualTo("email", idLower).limit(1).get().await()
            if (q3.documents.isNotEmpty()) return q3.documents[0].id
            val q4 = db.collection(coll).whereEqualTo("correo", idLower).limit(1).get().await()
            if (q4.documents.isNotEmpty()) return q4.documents[0].id
        }
        return null
    } else {
        // tratar como uid: comprobar teachers/{uid}, users/{uid}
        val d = db.collection("teachers").document(identifier).get().await()
        if (d.exists()) return identifier
        val u = db.collection("users").document(identifier).get().await()
        if (u.exists()) return identifier
        return null
    }
}

// Helper: asigna curso y grupo a un identificador (email o uid). Si identifier es email, intenta encontrar uid.
// Devuelve true si actualizó/creó correctamente.
suspend fun assignToCourseAndGroup(db: FirebaseFirestore, identifier: String, curso: String, grupo: String): Boolean {
    val cid = identifier.trim()
    var uidToUse: String?
    // buscar uid
    uidToUse = findUidByIdentifier(db, cid)
    var providedEmail: String? = null
    if (cid.contains("@")) providedEmail = cid.lowercase()
    // si no existe y el identificador es email, creamos documento en teachers con id igual al uid de users si existe
    if (uidToUse == null && cid.contains("@")) {
        // intentar encontrar usuario en users (para usar su id si existe)
        val q = db.collection("users").whereEqualTo("email", providedEmail).limit(1).get().await()
        if (q.documents.isNotEmpty()) {
            uidToUse = q.documents[0].id
        } else {
            // no existe user; generar id nuevo y crear ambos documentos para mantener asociación
            uidToUse = db.collection("teachers").document().id
            try {
                val baseTeacher = mutableMapOf<String, Any?>()
                baseTeacher["email"] = providedEmail
                baseTeacher["createdAt"] = com.google.firebase.Timestamp.now()
                db.collection("teachers").document(uidToUse).set(baseTeacher).await()
            } catch (_: Exception) { }
            try {
                val baseUser = mutableMapOf<String, Any?>()
                baseUser["email"] = providedEmail
                baseUser["createdAt"] = com.google.firebase.Timestamp.now()
                db.collection("users").document(uidToUse).set(baseUser).await()
            } catch (_: Exception) { }
        }
    }

    if (uidToUse == null) return false

    // intentar obtener nombre existente (desde teachers o users) para propagarlo al actualizar el perfil
    var nameCandidate: String? = null
    try {
        val tDoc = try { db.collection("teachers").document(uidToUse).get().await() } catch (_: Exception) { null }
        val uDoc = try { db.collection("users").document(uidToUse).get().await() } catch (_: Exception) { null }
        nameCandidate = try {
            tDoc?.getString("nombre") ?: tDoc?.getString("name") ?: uDoc?.getString("nombre") ?: uDoc?.getString("name") ?: uDoc?.getString("displayName")
        } catch (_: Exception) { null }
    } catch (_: Exception) { }

    // construir valores
    val cursoVal = if (curso.isBlank() || grupo.isBlank()) null else ("${curso.trim()}-${grupo.trim()}")
    try {
        if (cursoVal == null) {
            // si no se proporcionó ambos, solo escribir separado si uno está presente
            val updates = mutableMapOf<String, Any>()
            if (curso.isNotBlank()) updates["curso"] = curso
            if (grupo.isNotBlank()) updates["grupo"] = grupo
            if (providedEmail != null) updates["email"] = providedEmail
            if (!nameCandidate.isNullOrBlank()) {
                updates["name"] = nameCandidate
                updates["displayName"] = nameCandidate
            }
            if (updates.isNotEmpty()) {
                db.collection("teachers").document(uidToUse).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                try { db.collection("users").document(uidToUse).set(updates, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
            }
        } else {
            val map = hashMapOf<String, Any?>("curso" to cursoVal, "curso_simple" to curso.trim(), "grupo" to grupo.trim())
            if (providedEmail != null) map["email"] = providedEmail
            if (!nameCandidate.isNullOrBlank()) { map["name"] = nameCandidate; map["displayName"] = nameCandidate }
            db.collection("teachers").document(uidToUse).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
            try { db.collection("users").document(uidToUse).set(map, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
        }
        return true
    } catch (e: Exception) {
        return false
    }
}
