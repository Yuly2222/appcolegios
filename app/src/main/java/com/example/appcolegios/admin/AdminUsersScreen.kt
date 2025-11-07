package com.example.appcolegios.admin

import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.example.appcolegios.academico.EventType
import java.util.UUID

@Composable
fun AdminUsersScreen(navController: NavController? = null, onUserSelected: (String) -> Unit = {}) {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            // Preferir listar estudiantes (colección "students") porque la gestión de horarios aplica a estudiantes.
            val snap = db.collection("students").get().await()
            val list = mutableListOf<Map<String, Any>>()
            if (!snap.isEmpty) {
                for (doc in snap.documents) {
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    // Normalizar campos: id, displayName, email, role
                    map["id"] = doc.id
                    val nombre = (map["nombre"] as? String) ?: (map["name"] as? String) ?: "(sin nombre)"
                    map["displayName"] = nombre
                    map["email"] = map["email"] ?: ""
                    map["role"] = "ESTUDIANTE"
                    list.add(map)
                }
            } else {
                // Fallback: listar colección "users" si no hay estudiantes
                val us = db.collection("users").get().await()
                for (doc in us.documents) {
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    map["id"] = doc.id
                    list.add(map)
                }
            }
            users = list
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error") }
        return
    }

    Card(Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(contentPadding = PaddingValues(8.dp)) {
            items(users) { u ->
                val id = u["id"] as? String ?: ""
                val name = (u["displayName"] as? String) ?: (u["name"] as? String) ?: "(sin nombre)"
                val email = (u["email"] as? String) ?: ""
                val role = (u["role"] as? String) ?: ""
                var actionsOpen by remember { mutableStateOf(false) }
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable {
                            // navegar a detalle de perfil
                            navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminProfileDetail.route.replace("{userId}", id))
                        }) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(if (email.isNotBlank()) "$email · ${role}" else role, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { actionsOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Acciones") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(4.dp))

                    if (actionsOpen) {
                        // Dialog with user details and actions
                        AlertDialog(onDismissRequest = { actionsOpen = false }, title = { Text(name) }, text = {
                            Column {
                                Text("Email: $email")
                                Spacer(Modifier.height(8.dp))
                                Text("Rol: $role")
                            }
                        }, confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    actionsOpen = false
                                    // navigate to schedule manager for this user
                                    navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminScheduleManage.route.replace("{userId}", id))
                                }) { Icon(Icons.Filled.Schedule, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Agregar horario") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    actionsOpen = false
                                    // open simple notification dialog
                                    sendNotificationForUser(db, id, name)
                                }) { Icon(Icons.Filled.Notifications, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Enviar notificación") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    actionsOpen = false
                                    // open event dialog to add event to user's calendar
                                    addEventForUser(db, id, name)
                                }) { Icon(Icons.Filled.Event, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Agregar evento") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    actionsOpen = false
                                    // ver perfil (alternativa)
                                    navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminProfileDetail.route.replace("{userId}", id))
                                }) { Text("Ver perfil") }
                            }
                        }, dismissButton = { TextButton(onClick = { actionsOpen = false }) { Text("Cerrar") } })
                    }
                }
            }
        }
    }
}

// Helper: open a small UI to send notification; implemented as immediate write after simple default content for speed
private fun sendNotificationForUser(db: FirebaseFirestore, userId: String, senderName: String) {
    // For simplicity create a generic notification; in UI we could ask title/body — keeping it minimal
    try {
        val notif = hashMapOf(
            "titulo" to "Mensaje del Admin",
            "cuerpo" to "Has recibido una notificación del administrador.",
            "remitente" to "admin",
            "senderName" to senderName,
            "fechaHora" to Timestamp.now(),
            "leida" to false,
            "type" to "admin"
        )
        db.collection("users").document(userId).collection("notifications").add(notif)
    } catch (_: Exception) {}
}

// Helper: add a minimal event document under users/{userId}/events
private fun addEventForUser(db: FirebaseFirestore, userId: String, titleFallback: String) {
    try {
        val eid = UUID.randomUUID().toString()
        val data = hashMapOf<String, Any?>(
            "id" to eid,
            "title" to "Evento desde Admin",
            "description" to "Evento agregado por administrador para $titleFallback",
            "date" to Timestamp.now(),
            "type" to EventType.EVENTO.name,
            "createdAt" to Timestamp.now(),
            "ownerId" to userId
        )
        db.collection("users").document(userId).collection("events").document(eid).set(data)
        // create notification for the user as well
        val notif = hashMapOf(
            "titulo" to "Nuevo evento",
            "cuerpo" to "Se agregó un evento a tu calendario",
            "remitente" to "admin",
            "senderName" to "Administrador",
            "fechaHora" to Timestamp.now(),
            "leida" to false,
            "relatedId" to eid,
            "type" to "event"
        )
        db.collection("users").document(userId).collection("notifications").add(notif)
    } catch (_: Exception) {}
}
