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

@Composable
fun AssignGroupScreen(navController: NavController?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var identifier by remember { mutableStateOf("") }
    var curso by remember { mutableStateOf("") }
    var grupo by remember { mutableStateOf("") }
    var dialogLoading by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var foundName by remember { mutableStateOf<String?>(null) }

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
         content = { contentPadding ->
             Column(
                 modifier = Modifier
                     .fillMaxSize()
                     .verticalScroll(rememberScrollState())
                     .padding(16.dp)
                     .padding(contentPadding),
                 verticalArrangement = Arrangement.spacedBy(12.dp)
                 ) {

                 // Barra superior personalizada
                 Surface(
                     tonalElevation = 3.dp,
                     color = MaterialTheme.colorScheme.primaryContainer,
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Row(modifier = Modifier
                         .fillMaxWidth()
                         .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                         IconButton(onClick = { navController?.popBackStack() }) {
                             Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                         }
                         Text(text = "Asignar / Quitar curso y grupo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                     }
                 }

                 Text("Introduce el email o UID del docente. También puedes importar un CSV con un email por línea para asignación masiva.", style = MaterialTheme.typography.bodyMedium)

                 OutlinedTextField(
                     value = identifier,
                     onValueChange = { identifier = it },
                     label = { Text("Email o UID") },
                     singleLine = true,
                     modifier = Modifier.fillMaxWidth()
                 )

                 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                     OutlinedTextField(value = curso, onValueChange = { curso = it }, label = { Text("Curso (ej. 10)") }, singleLine = true, modifier = Modifier.weight(1f))
                     OutlinedTextField(value = grupo, onValueChange = { grupo = it }, label = { Text("Grupo (ej. A)") }, singleLine = true, modifier = Modifier.weight(1f))
                 }

                 Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                         Button(onClick = { filePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) }, modifier = Modifier.weight(1f)) {
                             Text("Importar CSV y asignar")
                         }
                         OutlinedButton(onClick = { exportLauncher.launch("docentes_export.csv") }, modifier = Modifier.weight(1f)) {
                             Text("Exportar docentes (CSV)")
                         }
                     }

                     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                 }

                 if (!foundName.isNullOrBlank()) Text("Docente: ${foundName} (uid=${foundUid})", style = MaterialTheme.typography.bodyMedium)
                 if (!dialogMessage.isNullOrBlank()) Text(dialogMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 if (dialogLoading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                 HorizontalDivider()

                 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
             }
         }
     )
 }
