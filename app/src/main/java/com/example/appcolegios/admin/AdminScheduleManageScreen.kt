package com.example.appcolegios.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.appcolegios.data.model.ClassSession
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

@Composable
fun AdminScheduleManageScreen(userIdArg: String? = null, onDone: () -> Unit = {}) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var userId by remember { mutableStateOf(userIdArg ?: "") }
    var sessions by remember { mutableStateOf<List<Pair<String, ClassSession>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Form state (used for create and edit)
    var editingId by remember { mutableStateOf<String?>(null) }
    var formDay by remember { mutableStateOf(1) } // 1..7
    var formSubject by remember { mutableStateOf("") }
    var formTeacher by remember { mutableStateOf("") }
    var formStart by remember { mutableStateOf("08:00") }
    var formEnd by remember { mutableStateOf("09:00") }
    var formClassroom by remember { mutableStateOf("") }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var toDeleteId by remember { mutableStateOf<String?>(null) }

    suspend fun loadSessionsFor(uid: String) {
        isLoading = true
        error = null
        try {
            val snap = db.collection("students").document(uid).collection("schedule").get().await()
            val list = mutableListOf<Pair<String, ClassSession>>()
            for (doc in snap.documents) {
                try {
                    val cs = doc.toObject(ClassSession::class.java)
                    if (cs != null) list.add(Pair(doc.id, cs))
                } catch (_: Exception) {}
            }
            // sort by dayOfWeek then startTime
            sessions = list.sortedWith(compareBy({ it.second.dayOfWeek }, { it.second.startTime }))
        } catch (e: Exception) {
            error = e.message
            sessions = emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        loadSessionsFor(userId)
    }

    fun resetForm() {
        editingId = null
        formDay = 1
        formSubject = ""
        formTeacher = ""
        formStart = "08:00"
        formEnd = "09:00"
        formClassroom = ""
    }

    fun fillFormFrom(id: String, cs: ClassSession) {
        editingId = id
        formDay = cs.dayOfWeek
        formSubject = cs.subject
        formTeacher = cs.teacher
        formStart = cs.startTime
        formEnd = cs.endTime
        formClassroom = cs.classroom
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gestionar Horario", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = userId, onValueChange = { userId = it }, label = { Text("UID de usuario (estudiante)") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                scope.launch {
                    if (userId.isBlank()) { error = "UID vacío"; return@launch }
                    loadSessionsFor(userId)
                }
            }) { Text("Cargar") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { resetForm() }) { Text("Nuevo") }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        // Formulario
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editingId == null) "Crear sesión" else "Editar sesión", style = MaterialTheme.typography.titleMedium)

                // Day of week selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Día:", modifier = Modifier.width(56.dp))
                    val days = listOf("Lun","Mar","Mié","Jue","Vie","Sáb","Dom")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) { Text(days[(formDay-1).coerceIn(0,6)]) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            days.forEachIndexed { idx, d ->
                                DropdownMenuItem(text = { Text(d) }, onClick = { formDay = idx+1; expanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(value = formSubject, onValueChange = { formSubject = it }, label = { Text("Materia") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = formTeacher, onValueChange = { formTeacher = it }, label = { Text("Profesor") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = formStart, onValueChange = { formStart = it }, label = { Text("Inicio (HH:mm)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = formEnd, onValueChange = { formEnd = it }, label = { Text("Fin (HH:mm)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = formClassroom, onValueChange = { formClassroom = it }, label = { Text("Aula") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        scope.launch {
                            if (userId.isBlank()) { error = "UID vacío"; return@launch }
                            if (formSubject.isBlank()) { error = "Materia vacía"; return@launch }
                            val cs = ClassSession(dayOfWeek = formDay, subject = formSubject, teacher = formTeacher, startTime = formStart, endTime = formEnd, classroom = formClassroom)
                            try {
                                if (editingId == null) {
                                    db.collection("students").document(userId).collection("schedule").add(cs).await()
                                } else {
                                    db.collection("students").document(userId).collection("schedule").document(editingId!!).set(cs).await()
                                }
                                // recargar
                                loadSessionsFor(userId)
                                resetForm()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }) { Text(if (editingId == null) "Guardar" else "Actualizar") }

                    if (editingId != null) {
                        OutlinedButton(onClick = { resetForm() }) { Text("Cancelar") }
                    }
                }

                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Sesiones", style = MaterialTheme.typography.titleMedium)

        if (sessions.isEmpty()) {
            Text("(sin sesiones)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(contentPadding = PaddingValues(4.dp)) {
                items(sessions) { pair ->
                    val id = pair.first
                    val s = pair.second
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                val days = listOf("Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo")
                                Text(s.subject, style = MaterialTheme.typography.titleMedium)
                                Text("${days.getOrNull(s.dayOfWeek-1) ?: s.dayOfWeek} · ${s.startTime} - ${s.endTime} · ${s.classroom}")
                            }
                            IconButton(onClick = { fillFormFrom(id, s) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                            IconButton(onClick = { toDeleteId = id; showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
    }

    if (showDeleteConfirm && toDeleteId != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false; toDeleteId = null }, title = { Text("Confirmar") }, text = { Text("Eliminar sesión?") }, confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        if (!toDeleteId.isNullOrBlank() && userId.isNotBlank()) {
                            db.collection("students").document(userId).collection("schedule").document(toDeleteId!!).delete().await()
                            loadSessionsFor(userId)
                        }
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        showDeleteConfirm = false
                        toDeleteId = null
                    }
                }
            }) { Text("Eliminar") }
        }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false; toDeleteId = null }) { Text("Cancelar") } })
    }
}
