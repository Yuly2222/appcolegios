package com.example.appcolegios.academico

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.UserData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel

@Composable
fun DatePickerCompose(initialDate: Date, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    // Simple month grid selector with controllable colors
    val calState = remember { mutableStateOf(Calendar.getInstance().apply { time = initialDate }) }
    val year = calState.value.get(Calendar.YEAR)
    val month = calState.value.get(Calendar.MONTH)
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calState.value.time)

    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = {
         Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { calState.value.add(Calendar.MONTH, -1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev") }
                Text(monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { calState.value.add(Calendar.MONTH, 1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next") }
            }

            Spacer(Modifier.height(8.dp))
            // build day grid
            val temp = Calendar.getInstance().apply { set(year, month, 1) }
            val firstDow = temp.get(Calendar.DAY_OF_WEEK) // 1=Sun
            val daysInMonth = temp.getActualMaximum(Calendar.DAY_OF_MONTH)
            val blanks = (firstDow - temp.firstDayOfWeek + 7) % 7

            Column {
                // weekday headers
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val df = SimpleDateFormat("EE", Locale.getDefault())
                    val headerCal = Calendar.getInstance()
                    for (i in 0 until 7) {
                        headerCal.set(Calendar.DAY_OF_WEEK, headerCal.firstDayOfWeek + i)
                        Text(df.format(headerCal.time), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // grid
                var day = 1
                val rows = ((blanks + daysInMonth) + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (c in 0 until 7) {
                            val index = r * 7 + c
                            if (index < blanks || day > daysInMonth) {
                                Box(modifier = Modifier.weight(1f).height(40.dp)) { }
                            } else {
                                val thisDay = day
                                val cellCal = Calendar.getInstance().apply { set(year, month, thisDay) }
                                val isToday = Calendar.getInstance().let { it.get(Calendar.YEAR)==cellCal.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR)==cellCal.get(Calendar.DAY_OF_YEAR) }
                                val selectedCal = Calendar.getInstance().apply { time = initialDate }
                                val isSelected = selectedCal.get(Calendar.YEAR) == cellCal.get(Calendar.YEAR) && selectedCal.get(Calendar.DAY_OF_YEAR) == cellCal.get(Calendar.DAY_OF_YEAR)
                                val bg = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val fg = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Box(modifier = Modifier.weight(1f).height(40.dp).padding(2.dp)) {
                                    Card(modifier = Modifier.fillMaxSize().clickable { onDateSelected(cellCal.time) }.background(bg), colors = CardDefaults.cardColors(containerColor = bg)) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(thisDay.toString(), color = fg)
                                        }
                                    }
                                }
                                day++
                            }
                        }
                    }
                }
            }
        }
    })
}

private const val MAX_ATTACHMENTS = 5
private const val MAX_ATTACHMENT_BYTES: Long = 10L * 1024L * 1024L // 10 MB

data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val dueDate: Date?,
    val attachments: List<String> = emptyList(), // download URLs
    val attachmentPaths: List<String> = emptyList() // storage paths for deletion
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseSimple>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseSimple?>(null) }
    val scope = rememberCoroutineScope()

    // Obtener rol del usuario para decidir si puede crear/editar tareas
    val userPrefs = remember { UserPreferencesRepository(context) }
    val currentUserData by userPrefs.userData.collectAsState(initial = UserData(null, null, null))
    val roleNormalized = (currentUserData.role ?: "").trim().uppercase(Locale.getDefault())
    val canCreate = when (roleNormalized) {
        "DOCENTE", "TEACHER", "PROFESOR", "ADMIN" -> true
        else -> false
    }

    // Perfil VM para padres
    val profileVm: ProfileViewModel = viewModel()
    val children by profileVm.children.collectAsState()
    val isParent = (currentUserData.role ?: "").equals("PARENT", ignoreCase = true) || (currentUserData.role ?: "").equals("PADRE", ignoreCase = true)
    var showSelectChildDialog by remember { mutableStateOf(false) }
    var selectedChildIndex by remember { mutableStateOf(0) }

    // Si el usuario es padre y tiene un hijo seleccionado, cargaremos los cursos de ese hijo
    val selectedChildId = children.getOrNull(selectedChildIndex)?.id

    // fields for task
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Date?>(null) }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }

    // attachments (URIs selected locally)
    val attachments = remember { mutableStateListOf<Uri>() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
        uris?.let { list ->
            // validate count and sizes
            val availableSlots = MAX_ATTACHMENTS - attachments.size
            if (list.size > availableSlots) {
                Toast.makeText(context, "Solo se permiten $MAX_ATTACHMENTS adjuntos (ya hay ${attachments.size})", Toast.LENGTH_LONG).show()
            }
            val toAdd = list.take(availableSlots)
            toAdd.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                // check size
                var ok = true
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val len = afd.length
                        if (len > 0 && len > MAX_ATTACHMENT_BYTES) {
                            ok = false
                        }
                    }
                } catch (_: Exception) { /* ignore size check if not available */ }
                if (!ok) {
                    Toast.makeText(context, "Archivo demasiado grande (>10MB): ${uri.lastPathSegment}", Toast.LENGTH_LONG).show()
                } else {
                    attachments.add(uri)
                }
            }
        }
    }

    // tasks list for course
    val tasksForCourse = remember { mutableStateListOf<TaskItem>() }
    var loadingTasks by remember { mutableStateOf(false) }

    // upload state
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) } // porcentaje general (0..100)
    val failedUploads = remember { mutableStateListOf<Uri>() }

    // edit state
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showDeleteConfirmFor by remember { mutableStateOf<TaskItem?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        errorMsg = null
        try {
            val user = auth.currentUser
            val loaded = mutableListOf<CourseSimple>()
            if (user != null && !isParent) {
                val q = firestore.collection("courses").whereEqualTo("teacherId", user.uid).get().await()
                if (!q.isEmpty) {
                    for (doc in q.documents) {
                        val id = doc.id
                        val name = doc.getString("name") ?: "Curso"
                        val studentsSnap = firestore.collection("courses").document(id).collection("students").get().await()
                        val studs = studentsSnap.documents.map { s -> StudentSimple(s.id, s.getString("name") ?: s.getString("displayName") ?: "Alumno") }
                        loaded.add(CourseSimple(id, name, studs))
                    }
                }
            }
            if (loaded.isEmpty()) {
                loaded.add(CourseSimple("c1","Demo Curso", listOf(StudentSimple("s1","Alumno A"), StudentSimple("s2","Alumno B"))))
            }
            courses = loaded
        } catch (e: Exception) {
            errorMsg = "Error cargando cursos: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    // Si es padre y se seleccionó un hijo, cargamos los cursos del hijo
    LaunchedEffect(selectedChildId) {
        if (!isParent) return@LaunchedEffect
        loading = true
        try {
            val childId = selectedChildId
            val loaded = mutableListOf<CourseSimple>()
            if (childId != null) {
                val studentDoc = firestore.collection("students").document(childId).get().await()
                val courseIds = studentDoc.get("courses") as? List<*>
                if (!courseIds.isNullOrEmpty()) {
                    for (cid in courseIds) {
                        val cdoc = firestore.collection("courses").document(cid.toString()).get().await()
                        if (cdoc.exists()) {
                            val studentsSnapshot = firestore.collection("courses").document(cdoc.id).collection("students").get().await()
                            val studs = studentsSnapshot.documents.map { s -> StudentSimple(s.id, s.getString("name") ?: s.getString("displayName") ?: "Alumno") }
                            loaded.add(CourseSimple(cdoc.id, cdoc.getString("name") ?: "Curso", studs))
                        }
                    }
                } else {
                    // fallback: buscar cursos por subcollection students
                    val allCourses = firestore.collection("courses").get().await()
                    for (doc in allCourses.documents) {
                        val sdoc = firestore.collection("courses").document(doc.id).collection("students").document(childId).get().await()
                        if (sdoc.exists()) {
                            val studs = listOf(StudentSimple(childId, sdoc.getString("name") ?: sdoc.getString("displayName") ?: "Alumno"))
                            loaded.add(CourseSimple(doc.id, doc.getString("name") ?: "Curso", studs))
                        }
                    }
                }
            }
            if (loaded.isEmpty()) {
                loaded.add(CourseSimple("c1","Demo Curso", listOf(StudentSimple("s1","Alumno A"), StudentSimple("s2","Alumno B"))))
            }
            courses = loaded
            selectedCourse = null
        } catch (_: Exception) {
            // ignore
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isParent) {
            // Selector de hijo
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = children.isNotEmpty()) { showSelectChildDialog = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val name = children.getOrNull(selectedChildIndex)?.nombre ?: "--"
                    val curso = children.getOrNull(selectedChildIndex)?.curso ?: ""
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Curso: $curso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (showSelectChildDialog) {
                var sel by remember { mutableStateOf(selectedChildIndex) }
                AlertDialog(onDismissRequest = { showSelectChildDialog = false }, title = { Text("Selecciona estudiante") }, text = {
                    Column {
                        if (children.isEmpty()) Text("No hay hijos asociados") else children.forEachIndexed { idx, ch ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { sel = idx }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = sel == idx, onClick = { sel = idx })
                                Spacer(Modifier.width(8.dp))
                                Column { Text(ch.nombre, fontWeight = FontWeight.SemiBold); Text("Curso: ${ch.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { if (children.isNotEmpty()) selectedChildIndex = sel; showSelectChildDialog = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") } })
            }
            Spacer(Modifier.height(8.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (selectedCourse == null) {
            Text("Cursos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(courses) { course ->
                    Card(modifier = Modifier.fillMaxWidth().clickable {
                        selectedCourse = course
                    }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${course.students.size} estudiantes", style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        } else {
            val course = selectedCourse!!
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { selectedCourse = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            // List existing tasks for this course
            Spacer(Modifier.height(8.dp))
            Text("Tareas existentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // load tasks lazily when entering course
            LaunchedEffect(course.id) {
                loadingTasks = true
                tasksForCourse.clear()
                try {
                    val snap = firestore.collection("tasks")
                        .whereEqualTo("courseId", course.id)
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get().await()
                    snap.documents.forEach { d ->
                        val id = d.id
                        val titleDoc = d.getString("title") ?: ""
                        val descDoc = d.getString("description") ?: ""
                        val due = (d.get("dueDate") as? com.google.firebase.Timestamp)?.toDate()
                        val atts = d.get("attachments") as? List<*> ?: emptyList<Any>()
                        val attsStr = atts.mapNotNull { it?.toString() }
                        val paths = d.get("attachmentPaths") as? List<*> ?: emptyList<Any>()
                        val pathsStr = paths.mapNotNull { it?.toString() }
                        tasksForCourse.add(TaskItem(id, titleDoc, descDoc, due, attsStr, pathsStr))
                    }
                } catch (_: Exception) {}
                loadingTasks = false
            }

            if (loadingTasks) CircularProgressIndicator() else {
                if (tasksForCourse.isEmpty()) {
                    Text("No hay tareas publicadas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasksForCourse.forEach { t ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(t.description.take(150), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val d = t.dueDate
                                        if (d != null) Spacer(Modifier.height(6.dp))
                                        if (d != null) Text("Fecha: ${dateFormat.format(d)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (canCreate) {
                                        Column {
                                            IconButton(onClick = { editingTask = t }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                                            IconButton(onClick = { showDeleteConfirmFor = t }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (canCreate) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título de la tarea") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Spacer(Modifier.height(8.dp))

                // attachments UI
                Text("Adjuntos (max $MAX_ATTACHMENTS, max 10MB cada)")
                Spacer(Modifier.height(4.dp))
                if (attachments.isEmpty()) Text("No hay archivos adjuntos seleccionados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                attachments.forEachIndexed { idx, uri ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(uri.lastPathSegment ?: uri.toString(), modifier = Modifier.weight(1f))
                        IconButton(onClick = { attachments.removeAt(idx) }) { Icon(Icons.Filled.Close, contentDescription = "Quitar") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = attachments.size < MAX_ATTACHMENTS) {
                        Text("Adjuntar archivos")
                    }
                    if (attachments.size >= MAX_ATTACHMENTS) Text("Máximo alcanzado", color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(8.dp))

                // Fecha: usar picker Compose controlable de colores
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDatePicker = true }) { Text(if (dueDate == null) "Seleccionar fecha" else dateFormat.format(dueDate!!)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (title.isBlank()) { Toast.makeText(context, "Título requerido", Toast.LENGTH_SHORT).show(); return@Button }
                        if (dueDate == null) { Toast.makeText(context, "Fecha requerida", Toast.LENGTH_SHORT).show(); return@Button }
                        if (uploading) { Toast.makeText(context, "Subiendo... espere", Toast.LENGTH_SHORT).show(); return@Button }

                        scope.launch {
                            uploading = true
                            failedUploads.clear()
                            uploadProgress = 0
                            val uploadedUrls = mutableListOf<String>()
                            val uploadedPaths = mutableListOf<String>()

                            try {
                                val total = attachments.size.takeIf { it > 0 } ?: 0
                                var completed = 0
                                for (uri in attachments.toList()) {
                                    try {
                                        val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
                                        val path = "tasks/${course.id}/${System.currentTimeMillis()}_${filename}"
                                        val ref = storage.reference.child(path)
                                        val input = context.contentResolver.openInputStream(uri)
                                        if (input != null) {
                                            ref.putStream(input).await()
                                            val url = ref.downloadUrl.await().toString()
                                            uploadedUrls.add(url)
                                            uploadedPaths.add(path)
                                        } else {
                                            failedUploads.add(uri)
                                        }
                                    } catch (e: Exception) {
                                        failedUploads.add(uri)
                                    }
                                    completed++
                                    uploadProgress = if (total > 0) (completed * 100 / total) else 100
                                }

                                val task = hashMapOf(
                                    "courseId" to course.id,
                                    "courseName" to course.name,
                                    "teacherId" to (auth.currentUser?.uid ?: ""),
                                    "title" to title,
                                    "description" to description,
                                    "dueDate" to com.google.firebase.Timestamp(dueDate!!),
                                    "attachments" to uploadedUrls,
                                    "attachmentPaths" to uploadedPaths,
                                    "createdAt" to com.google.firebase.Timestamp.now()
                                )
                                val ref = firestore.collection("tasks").add(task).await()

                                // Crear notificaciones para cada estudiante
                                course.students.forEach { s ->
                                    val notif = hashMapOf(
                                        "titulo" to "Nueva tarea: $title",
                                        "cuerpo" to (description.ifBlank { "Nueva tarea asignada" }).take(200),
                                        "remitente" to (auth.currentUser?.email ?: "Profesor"),
                                        "fechaHora" to com.google.firebase.Timestamp.now(),
                                        "leida" to false,
                                        "relatedId" to ref.id,
                                        "type" to "task"
                                    )
                                    firestore.collection("users").document(s.id).collection("notifications").add(notif)
                                }

                                Toast.makeText(context, "Tarea creada", Toast.LENGTH_SHORT).show()
                                // limpiar
                                title = ""; description = ""; dueDate = null
                                attachments.clear()

                                // recargar tasks
                                // force reload by re-triggering LaunchedEffect for course.id
                                // simplest: reload manually
                                loadingTasks = true
                                tasksForCourse.clear()
                                try {
                                    val snap2 = firestore.collection("tasks").whereEqualTo("courseId", course.id).orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).get().await()
                                    snap2.documents.forEach { d ->
                                        val id = d.id
                                        val titleDoc = d.getString("title") ?: ""
                                        val descDoc = d.getString("description") ?: ""
                                        val due = (d.get("dueDate") as? com.google.firebase.Timestamp)?.toDate()
                                        val atts = d.get("attachments") as? List<*> ?: emptyList<Any>()
                                        val attsStr = atts.mapNotNull { it?.toString() }
                                        val paths = d.get("attachmentPaths") as? List<*> ?: emptyList<Any>()
                                        val pathsStr = paths.mapNotNull { it?.toString() }
                                        tasksForCourse.add(TaskItem(id, titleDoc, descDoc, due, attsStr, pathsStr))
                                    }
                                } catch (_: Exception) {}
                                loadingTasks = false

                            } catch (e: Exception) {
                                Toast.makeText(context, "Error creando tarea: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                uploading = false
                                uploadProgress = 0
                            }
                        }
                    }) { Text("Crear tarea") }
                }

                // picker compose dialog
                if (showDatePicker) {
                    DatePickerCompose(initialDate = dueDate ?: Date(), onDateSelected = { d -> dueDate = d; showDatePicker = false }, onDismiss = { showDatePicker = false })
                }

                Spacer(Modifier.height(16.dp))
                Text("Estudiantes en el curso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(course.students) { s ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    Text(s.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // uploading progress UI & retry
    if (uploading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Subiendo adjuntos") },
            text = {
                Column {
                    // LinearProgressIndicator espera ahora un lambda `progress` (evita la sobrecarga deprecada)
                    LinearProgressIndicator(progress = { uploadProgress / 100f })
                     Spacer(Modifier.height(8.dp))
                     Text("$uploadProgress%")
                 }
             },
             confirmButton = {}
         )
     }

    if (failedUploads.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { failedUploads.clear() },
            title = { Text("Subidas fallidas") },
            text = {
                Column {
                    Text("Fallo al subir ${failedUploads.size} archivos. Puedes reintentar o crear la tarea sin ellos.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // retry uploads for failed
                    scope.launch {
                        val toRetry = failedUploads.toList()
                        failedUploads.clear()
                        uploading = true
                        val uploadedUrls = mutableListOf<String>()
                        val uploadedPaths = mutableListOf<String>()
                        var completed = 0
                        for (uri in toRetry) {
                            try {
                                val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
                                val path = "tasks/retry_${System.currentTimeMillis()}_${filename}"
                                val ref = storage.reference.child(path)
                                val input = context.contentResolver.openInputStream(uri)
                                if (input != null) {
                                    ref.putStream(input).await()
                                    val url = ref.downloadUrl.await().toString()
                                    uploadedUrls.add(url)
                                    uploadedPaths.add(path)
                                }
                            } catch (e: Exception) {
                                failedUploads.add(uri)
                            }
                            completed++
                            uploadProgress = (completed * 100 / toRetry.size)
                        }
                        uploading = false
                        uploadProgress = 0
                    }
                }) {
                    Text("Reintentar")
                }
            },
            dismissButton = {
                TextButton(onClick = { failedUploads.clear() }) {
                    Text("Ignorar")
                }
            }
        )
    }

    // Edit task dialog
    if (editingTask != null) {
        val t = editingTask!!
        var eTitle by remember { mutableStateOf(t.title) }
        var eDesc by remember { mutableStateOf(t.description) }
        var eDate by remember { mutableStateOf(t.dueDate) }
        val eAttachments = remember { mutableStateListOf<String>().apply { addAll(t.attachments) } }
        var showDate by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingTask = null },
            title = { Text("Editar tarea") },
            text = {
                Column {
                    OutlinedTextField(value = eTitle, onValueChange = { eTitle = it }, label = { Text("Título") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = eDesc, onValueChange = { eDesc = it }, label = { Text("Descripción") }, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { showDate = true }) { Text(if (eDate == null) "Seleccionar fecha" else dateFormat.format(eDate!!)) }
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Adjuntos existentes:")
                    eAttachments.forEach { url -> Text(url, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            firestore.collection("tasks").document(t.id).update(mapOf(
                                "title" to eTitle,
                                "description" to eDesc,
                                "dueDate" to if (eDate != null) com.google.firebase.Timestamp(eDate!!) else null
                            ))
                            val idx = tasksForCourse.indexOfFirst { it.id == t.id }
                            if (idx >= 0) {
                                tasksForCourse[idx] = tasksForCourse[idx].copy(title = eTitle, description = eDesc, dueDate = eDate)
                            }
                            editingTask = null
                            Toast.makeText(context, "Tarea actualizada", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error actualizando: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTask = null }) {
                    Text("Cancelar")
                }
            }
        )

        if (showDate) {
            DatePickerCompose(initialDate = eDate ?: Date(), onDateSelected = { d: Date -> eDate = d; showDate = false }, onDismiss = { showDate = false })
        }
    }

    // Delete confirmation
    if (showDeleteConfirmFor != null) {
        val t = showDeleteConfirmFor!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmFor = null },
            title = { Text("Eliminar tarea") },
            text = { Text("¿Eliminar la tarea '${t.title}'? Esto eliminará la tarea y opcionalmente los archivos en Storage.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            t.attachmentPaths.forEach { p ->
                                try { storage.reference.child(p).delete().await() } catch (_: Exception) {}
                            }
                            firestore.collection("tasks").document(t.id).delete().await()
                            tasksForCourse.removeAll { it.id == t.id }
                            Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error eliminando: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            showDeleteConfirmFor = null
                        }
                    }
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmFor = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
