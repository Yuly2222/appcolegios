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
import com.example.appcolegios.data.FirebaseRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.example.appcolegios.academico.EventType
import java.util.UUID
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.tasks.await

@Composable
fun AdminUsersScreen(navController: NavController? = null, mode: String = "view", onUserSelected: (String) -> Unit = {}) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // UI states for dialogs
    var showDeleteConfirmUserId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmUserName by remember { mutableStateOf<String?>(null) }

    var composeNotificationForUserId by remember { mutableStateOf<String?>(null) }
    var composeNotificationForUserName by remember { mutableStateOf<String?>(null) }
    var notifTitle by remember { mutableStateOf("") }
    var notifBody by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val repo = FirebaseRepository()
            users = repo.fetchAdminUserList()
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
        // Usar LazyColumn para lista; el contenido del card puede necesitar scroll vertical en pantallas pequeñas
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
                        // Columna clicable correctamente balanceada
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (mode == "manage") {
                                        // Ir directo a gestionar horario, sin pasar por detalle
                                        val route = com.example.appcolegios.navigation.AppRoutes.AdminScheduleManage.route.replace("{userId}", id)
                                        try { navController?.navigate(route) } catch (_: Exception) {}
                                        try { onUserSelected(id) } catch (_: Exception) {}
                                    } else {
                                        // navegar a detalle de perfil
                                        try { navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminProfileDetail.route.replace("{userId}", id)) } catch (_: Exception) {}
                                    }
                                }
                        ) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(if (email.isNotBlank()) "$email · ${role}" else role, style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(onClick = { actionsOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Acciones") }
                    }

                    // Reemplazo de HorizontalDivider por Divider
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(4.dp))

                    if (actionsOpen) {
                        // Dialog with user details and actions
                        if (mode == "manage") {
                            AlertDialog(onDismissRequest = { actionsOpen = false }, title = { Text(name) }, text = {
                                Column {
                                    Text("Email: $email")
                                    Spacer(Modifier.height(8.dp))
                                    Text("Rol: $role")
                                }
                            }, confirmButton = {
                                Column {
                                    // Agregar horario (principal en manage)
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        val route = com.example.appcolegios.navigation.AppRoutes.AdminScheduleManage.route.replace("{userId}", id)
                                        if (navController != null) {
                                            try {
                                                navController.navigate(route)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No se pudo abrir gestor de horarios: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "NavController no disponible", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Icon(Icons.Filled.Schedule, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Agregar horario") }
                                    Spacer(Modifier.height(6.dp))
                                    // Enviar notificación debajo de agregar horario (abre diálogo para componer)
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        composeNotificationForUserId = id
                                        composeNotificationForUserName = name
                                    }) { Icon(Icons.Filled.Notifications, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Enviar notificación") }
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        addEventForUser(db, id, name)
                                    }) { Icon(Icons.Filled.Event, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Agregar evento") }
                                    Spacer(Modifier.height(6.dp))
                                    // Abrir el AssignGroupDialog centralizado
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        try { navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AssignGroup.route) } catch (_: Exception) {}
                                    }) { Text("Asignar curso") }
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminProfileDetail.route.replace("{userId}", id))
                                    }) { Text("Ver perfil") }
                                }
                            }, dismissButton = { TextButton(onClick = { actionsOpen = false }) { Text("Cerrar") } })
                        } else {
                            // modo view: solo editar o eliminar
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
                                        // editar: navegar a detalle de perfil para edición
                                        navController?.navigate(com.example.appcolegios.navigation.AppRoutes.AdminProfileDetail.route.replace("{userId}", id))
                                    }) { Text("Editar") }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        actionsOpen = false
                                        // Abrir diálogo de confirmación
                                        showDeleteConfirmUserId = id
                                        showDeleteConfirmUserName = name
                                    }) { Text("Eliminar") }
                                }
                            }, dismissButton = { TextButton(onClick = { actionsOpen = false }) { Text("Cerrar") } })
                        }
                    }
                }
            }
        }
        // Delete confirmation dialog
        if (showDeleteConfirmUserId != null) {
            val uidToDelete = showDeleteConfirmUserId
            val uname = showDeleteConfirmUserName ?: ""
            AlertDialog(onDismissRequest = { showDeleteConfirmUserId = null; showDeleteConfirmUserName = null }, title = { Text("Eliminar usuario") }, text = { Text("¿Seguro que deseas eliminar a $uname?") }, confirmButton = {
                TextButton(onClick = {
                    // realizar borrado
                    scope.launch {
                        try {
                            db.collection("users").document(uidToDelete!!).delete().await()
                        } catch (_: Exception) {}
                        try { db.collection("students").document(uidToDelete!!).delete().await() } catch (_: Exception) {}
                        try { db.collection("teachers").document(uidToDelete!!).delete().await() } catch (_: Exception) {}
                        users = users.filterNot { (it["id"] as? String) == uidToDelete }
                        Toast.makeText(context, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                    }
                    showDeleteConfirmUserId = null
                    showDeleteConfirmUserName = null
                }) { Text("Eliminar") }
            }, dismissButton = { TextButton(onClick = { showDeleteConfirmUserId = null; showDeleteConfirmUserName = null }) { Text("Cancelar") } })
        }

        // Compose notification dialog
        if (composeNotificationForUserId != null) {
            val uid = composeNotificationForUserId
            val uname = composeNotificationForUserName ?: ""
            AlertDialog(onDismissRequest = { composeNotificationForUserId = null; composeNotificationForUserName = null }, title = { Text("Enviar notificación a $uname") }, text = {
                Column {
                    OutlinedTextField(value = notifTitle, onValueChange = { notifTitle = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = notifBody, onValueChange = { notifBody = it }, label = { Text("Cuerpo") }, modifier = Modifier.fillMaxWidth())
                }
            }, confirmButton = {
                TextButton(onClick = {
                    // enviar notificación compuesta
                    scope.launch {
                        sendNotificationForUserWithContent(db, uid!!, notifTitle, notifBody)
                        Toast.makeText(context, "Notificación enviada", Toast.LENGTH_SHORT).show()
                    }
                    // limpiar y cerrar
                    notifTitle = ""
                    notifBody = ""
                    composeNotificationForUserId = null
                    composeNotificationForUserName = null
                }) { Text("Enviar") }
            }, dismissButton = { TextButton(onClick = { composeNotificationForUserId = null; composeNotificationForUserName = null }) { Text("Cancelar") } })
        }
    }
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

// Send notification with custom title and body content (non-suspending)
private fun sendNotificationForUserWithContent(db: FirebaseFirestore, userId: String, title: String, body: String) {
    try {
        val notif = hashMapOf(
            "titulo" to title,
            "cuerpo" to body,
            "remitente" to "admin",
            "senderName" to "Administrador",
            "fechaHora" to Timestamp.now(),
            "leida" to false,
            "type" to "admin"
        )
        // No await here: encolar la escritura y dejar que Firebase la maneje
        db.collection("users").document(userId).collection("notifications").add(notif)
    } catch (_: Exception) {}
}
