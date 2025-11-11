package com.example.appcolegios.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AdminParentsScreen(onDone: (() -> Unit)? = null) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var parentEmail by remember { mutableStateOf("") }
    var childIdentifier by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // file picker to import CSV (format: parentEmail,childEmailOrId) per line
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                status = null
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val reader = BufferedReader(InputStreamReader(input))
                        var line: String?
                        var processed = 0
                        var assigned = 0
                        var failed = 0
                        while (true) {
                            line = reader.readLine() ?: break
                            val parts = line.split(",").map { it.trim() }
                            if (parts.isEmpty()) continue
                            val pEmail = parts.getOrNull(0)?.takeIf { it.contains("@") } ?: continue
                            val childIdent = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
                            processed++
                            val (ok, msg) = assignParentToChild(db, pEmail, childIdent)
                            if (ok) assigned++ else failed++
                        }
                        status = "Procesados: $processed, asignados: $assigned, fallidos: $failed"
                    }
                } catch (e: Exception) {
                    status = "Error importando: ${e.message}"
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gestión de Padres", style = MaterialTheme.typography.headlineSmall)
        Text("Asigna un hijo a un padre. Puedes usar email del padre y luego buscar por email o id del hijo (estudiante). También puedes importar CSV: parentEmail,childEmailOrId")

        OutlinedTextField(value = parentEmail, onValueChange = { parentEmail = it }, label = { Text("Email del padre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(value = childIdentifier, onValueChange = { childIdentifier = it }, label = { Text("Email o UID del hijo") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                if (parentEmail.isBlank() || childIdentifier.isBlank()) { status = "Rellena ambos campos"; return@Button }
                scope.launch {
                    loading = true; status = null
                    val (ok, msg) = assignParentToChild(db, parentEmail.trim(), childIdentifier.trim())
                    status = msg
                    loading = false
                }
            }, modifier = Modifier.weight(1f)) { Text("Asignar") }

            // Diagnostic: buscar parent/child sin asignar
            OutlinedButton(onClick = {
                if (parentEmail.isBlank() && childIdentifier.isBlank()) { status = "Introduce email del padre o identificador del hijo para buscar"; return@OutlinedButton }
                scope.launch {
                    loading = true
                    status = null
                    val info = lookupParentChildInfo(db, parentEmail.trim(), childIdentifier.trim())
                    val parts = mutableListOf<String>()
                    if (!info.parentId.isNullOrBlank()) parts.add("Padre encontrado: ${info.parentId} (colección=${info.parentCollection}, campo=${info.parentField})") else parts.add("Padre NO encontrado")
                    if (!info.childId.isNullOrBlank()) parts.add("Hijo encontrado: ${info.childId} (colección=${info.childCollection}, campo=${info.childField})") else parts.add("Hijo NO encontrado")
                    status = parts.joinToString("; ")
                    loading = false
                }
            }, modifier = Modifier.weight(1f)) { Text("Buscar") }

            Button(onClick = { filePicker.launch(arrayOf("text/*","text/csv","text/comma-separated-values")) }, modifier = Modifier.weight(1f)) { Text("Importar CSV") }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (!status.isNullOrBlank()) Text(status!!)

        Spacer(Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onDone?.invoke() }) { Text("Cerrar") }
        }
    }
}

// helper data class
data class LookupInfo(
    val parentId: String?, val parentCollection: String?, val parentField: String?,
    val childId: String?, val childCollection: String?, val childField: String?
)

suspend fun lookupParentChildInfo(db: FirebaseFirestore, parentEmail: String, childIdentifier: String): LookupInfo {
    return withContext(Dispatchers.IO) {
        var parentId: String? = null
        var parentColl: String? = null
        var parentField: String? = null
        val normalizedParentEmail = parentEmail.trim()
        val emailCandidates = listOf(normalizedParentEmail, normalizedParentEmail.lowercase())
        val parentCollections = listOf("parents", "users")
        val emailFields = listOf("email", "correo", "mail", "correo_electronico", "displayName")
        try {
            for (coll in parentCollections) {
                for (field in emailFields) {
                    for (candidate in emailCandidates) {
                        val q = db.collection(coll).whereEqualTo(field, candidate).limit(1).get().await()
                        if (q.documents.isNotEmpty()) {
                            parentId = q.documents[0].id
                            parentColl = coll
                            parentField = field
                            break
                        }
                    }
                    if (parentId != null) break
                }
                if (parentId != null) break
            }
        } catch (_: Exception) {}

        var childId: String? = null
        var childColl: String? = null
        var childField: String? = null
        val normalizedChild = childIdentifier.trim()
        try {
            if (normalizedChild.contains("@")) {
                val candidates = listOf(normalizedChild, normalizedChild.lowercase())
                val studentFields = listOf("email", "correo", "mail")
                for (candidate in candidates) {
                    for (f in studentFields) {
                        val qs = db.collection("students").whereEqualTo(f, candidate).limit(1).get().await()
                        if (qs.documents.isNotEmpty()) { childId = qs.documents[0].id; childColl = "students"; childField = f; break }
                    }
                    if (childId != null) break
                }
                if (childId == null) {
                    val userFields = listOf("email", "correo", "mail", "displayName")
                    for (candidate in candidates) {
                        for (f in userFields) {
                            val qu = db.collection("users").whereEqualTo(f, candidate).limit(1).get().await()
                            if (qu.documents.isNotEmpty()) { childId = qu.documents[0].id; childColl = "users"; childField = f; break }
                        }
                        if (childId != null) break
                    }
                }
            } else {
                val sdoc = db.collection("students").document(normalizedChild).get().await()
                if (sdoc.exists()) { childId = normalizedChild; childColl = "students"; childField = "id" }
                else {
                    val udoc = db.collection("users").document(normalizedChild).get().await()
                    if (udoc.exists()) { childId = normalizedChild; childColl = "users"; childField = "id" }
                }
            }
        } catch (_: Exception) {}

        LookupInfo(parentId, parentColl, parentField, childId, childColl, childField)
    }
}

// update assignParentToChild to use lookup info (keep existing behavior but with clearer messages)
suspend fun assignParentToChild(db: FirebaseFirestore, parentEmail: String, childIdentifier: String): Pair<Boolean, String> {
    val pEmail = parentEmail.trim()
    val cIdent = childIdentifier.trim()
    try {
        val info = lookupParentChildInfo(db, pEmail, cIdent)
        var parentId = info.parentId
        if (parentId == null) {
            // crear parent
            val newDoc = db.collection("parents").document()
            val map = hashMapOf("email" to pEmail, "createdAt" to Timestamp.now())
            newDoc.set(map).await()
            parentId = newDoc.id
            try { db.collection("users").document(parentId).set(map).await() } catch (_: Exception) {}
        }
        if (info.childId == null) return Pair(false, "No se encontró el hijo; prueba con el UID o revisa en Firestore")
        val childId = info.childId
        // crear relación
        try { db.collection("parents").document(parentId).collection("children").document(childId).set(hashMapOf("childId" to childId, "linkedAt" to Timestamp.now())).await() } catch (_: Exception) {}
        val studentDoc = db.collection("students").document(childId).get().await()
        if (studentDoc.exists()) {
            db.collection("students").document(childId).update(mapOf("parents" to com.google.firebase.firestore.FieldValue.arrayUnion(parentId))).await()
            // Also set acudienteId/acudienteEmail for compatibility with ProfileViewModel
            try {
                db.collection("students").document(childId).update(mapOf("acudienteId" to parentId, "acudienteEmail" to pEmail)).await()
            } catch (_: Exception) { }
        } else {
            // actualizar users/{childId} como fallback
            try { db.collection("users").document(childId).set(mapOf("parents" to listOf(parentId)), com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) { try { db.collection("users").document(childId).update(mapOf("parents" to com.google.firebase.firestore.FieldValue.arrayUnion(parentId))).await() } catch (_: Exception) {} }
            // If student doc does not exist, also try to set acudienteEmail on users document for backward compatibility
            try { db.collection("users").document(childId).set(mapOf("acudienteEmail" to pEmail), com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
        }
        try { db.collection("users").document(parentId).collection("children").document(childId).set(hashMapOf("childId" to childId)).await() } catch (_: Exception) {}
        return Pair(true, "Asignación guardada: parentId=$parentId, childId=$childId")
    } catch (e: Exception) {
        return Pair(false, "Error asignando: ${e.message}")
    }
}
