@file:Suppress("unused")

package com.example.appcolegios.academico

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appcolegios.data.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel

enum class AttendanceStatus {
    PRESENTE, AUSENTE, TARDE, JUSTIFICADO
}

// Usamos los modelos centralizados en Models.kt: CourseSimple y StudentSimple
// Agregado: representación simple de un registro de asistencia usado por AttendanceRecordItem
data class AttendanceRecord(
    val date: Date,
    val status: AttendanceStatus,
    val observations: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    val userPrefs = UserPreferencesRepository(context)
    val userData by userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val role = userData.role ?: ""
    // Perfil VM para obtener hijos cuando el usuario es padre
    val profileVm: ProfileViewModel = viewModel()
    val children by profileVm.children.collectAsState()
    val isParent = (userData.role ?: "").equals("PARENT", ignoreCase = true) || (userData.role ?: "").equals("PADRE", ignoreCase = true)

    // Estado local para selección de hijo
    var showSelectChildDialog by remember { mutableStateOf(false) }
    var selectedChildIndex by remember { mutableStateOf(0) }

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseSimple>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseSimple?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Estado de asistencia por estudiante (solo usado por docentes)
    val attendanceMap = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        errorMsg = null
        try {
            val user = auth.currentUser
            val loadedCourses = mutableListOf<CourseSimple>()
            if (user != null) {
                if (role.equals("DOCENTE", ignoreCase = true) || (user.email?.endsWith("@school.edu") == true)) {
                    // Vista docente (comportamiento previo)
                    val coursesQuery = firestore.collection("courses").whereEqualTo("teacherId", user.uid).get().await()
                    if (!coursesQuery.isEmpty) {
                        for (doc in coursesQuery.documents) {
                            val courseId = doc.id
                            val courseName = doc.getString("name") ?: "Curso"
                            val studentsSnapshot = firestore.collection("courses").document(courseId).collection("students").get().await()
                            val students = studentsSnapshot.documents.map { sdoc ->
                                StudentSimple(sdoc.id, sdoc.getString("name") ?: sdoc.getString("displayName") ?: "Alumno")
                            }
                            loadedCourses.add(CourseSimple(courseId, courseName, students))
                        }
                    }
                } else if (isParent) {
                    // No cargamos cursos globales aquí; la vista de padre seleccionará un hijo y mostraremos sus registros directamente
                } else {
                    // Vista estudiante: cargar cursos en los que está inscrito
                    // Intentar leer desde students/{uid} campo 'courses' (lista de ids)
                    val studentDoc = firestore.collection("students").document(user.uid).get().await()
                    val courseIds = studentDoc.get("courses") as? List<*>
                    if (!courseIds.isNullOrEmpty()) {
                        for (cid in courseIds) {
                            val cdoc = firestore.collection("courses").document(cid.toString()).get().await()
                            if (cdoc.exists()) {
                                val studentsSnapshot = firestore.collection("courses").document(cdoc.id).collection("students").get().await()
                                val students = studentsSnapshot.documents.map { sdoc -> StudentSimple(sdoc.id, sdoc.getString("name") ?: sdoc.getString("displayName") ?: "Alumno") }
                                loadedCourses.add(CourseSimple(cdoc.id, cdoc.getString("name") ?: "Curso", students))
                            }
                        }
                    } else {
                        // Fallback: buscar cursos que contengan al estudiante en subcollection students
                        val allCourses = firestore.collection("courses").get().await()
                        for (doc in allCourses.documents) {
                            val studentsSnapshot = firestore.collection("courses").document(doc.id).collection("students").document(user.uid).get().await()
                            if (studentsSnapshot.exists()) {
                                val students = listOf(StudentSimple(user.uid, studentsSnapshot.getString("name") ?: studentsSnapshot.getString("displayName") ?: "Alumno"))
                                loadedCourses.add(CourseSimple(doc.id, doc.getString("name") ?: "Curso", students))
                            }
                        }
                    }
                }
            }

            if (loadedCourses.isEmpty()) {
                // fallback demo
                loadedCourses.add(
                    CourseSimple(
                        id = "c1",
                        name = "Matemáticas 101",
                        students = listOf(
                            StudentSimple("s1", "Juan Pérez"),
                            StudentSimple("s2", "María Gómez"),
                            StudentSimple("s3", "Carlos Ruiz")
                        )
                    )
                )
            }

            courses = loadedCourses
        } catch (e: Exception) {
            errorMsg = "Error cargando cursos: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Si es padre, mostrar selector de hijo en la parte superior
        if (isParent) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = children.isNotEmpty()) { showSelectChildDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val name = if (children.isNotEmpty()) children.getOrNull(selectedChildIndex)?.nombre ?: "--" else "--"
                    val curso = if (children.isNotEmpty()) children.getOrNull(selectedChildIndex)?.curso ?: "" else ""
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Curso: $curso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Diálogo para seleccionar hijo
            if (showSelectChildDialog) {
                var sel by remember { mutableStateOf(selectedChildIndex) }
                AlertDialog(onDismissRequest = { showSelectChildDialog = false }, title = { Text("Selecciona estudiante") }, text = {
                    Column {
                        if (children.isEmpty()) Text("No hay hijos asociados") else children.forEachIndexed { idx, ch ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { sel = idx }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = sel == idx, onClick = { sel = idx })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(ch.nombre, fontWeight = FontWeight.SemiBold)
                                    Text("Curso: ${ch.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        if (children.isNotEmpty()) selectedChildIndex = sel
                        showSelectChildDialog = false
                    }) { Text("Aceptar") }
                }, dismissButton = {
                    TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") }
                })
            }

            // Mostrar registros de asistencia para el hijo seleccionado (lectura)
            val childId = children.getOrNull(selectedChildIndex)?.id
            if (childId == null) {
                Text("No hay hijos asociados para mostrar.")
                return@Column
            } else {
                // Cargar registros de asistencia del childId similar a la rama de estudiante
                var attendanceRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
                LaunchedEffect(childId) {
                    attendanceRecords = emptyList()
                    try {
                        val query = firestore.collection("attendances").whereEqualTo("studentId", childId)
                        val snaps = query.get().await()
                        val list = mutableListOf<AttendanceRecord>()
                        for (doc in snaps.documents) {
                            val ts = doc.getTimestamp("date")?.toDate() ?: continue
                            val raw = (doc.get("records") as? Map<*, *>)?.get(childId) as? String
                            val status = when (raw?.uppercase()) {
                                "PRESENTE" -> AttendanceStatus.PRESENTE
                                "TARDE" -> AttendanceStatus.TARDE
                                "JUSTIFICADO" -> AttendanceStatus.JUSTIFICADO
                                else -> AttendanceStatus.AUSENTE
                            }
                            val obs = doc.getString("observations") ?: ""
                            list.add(AttendanceRecord(ts, status, obs))
                        }
                        attendanceRecords = list.sortedByDescending { it.date }
                    } catch (_: Exception) { }
                }

                if (attendanceRecords.isEmpty()) {
                    Text("No se encuentran registros de asistencia para este estudiante.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attendanceRecords) { rec -> AttendanceRecordItem(rec) }
                    }
                }
                return@Column
            }
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
                        attendanceMap.clear()
                        course.students.forEach { s -> attendanceMap[s.id] = AttendanceStatus.PRESENTE }
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
            var cutoffDays by remember { mutableStateOf<Int?>(30) }
            var expandedFilter by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { selectedCourse = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { expandedFilter = true }) { Text(cutoffDays?.let { "Últimos ${it}d" } ?: "Todos") }
                    DropdownMenu(expanded = expandedFilter, onDismissRequest = { expandedFilter = false }) {
                        DropdownMenuItem(text = { Text("7 días") }, onClick = { cutoffDays = 7; expandedFilter = false })
                        DropdownMenuItem(text = { Text("30 días") }, onClick = { cutoffDays = 30; expandedFilter = false })
                        DropdownMenuItem(text = { Text("90 días") }, onClick = { cutoffDays = 90; expandedFilter = false })
                        DropdownMenuItem(text = { Text("Todos") }, onClick = { cutoffDays = null; expandedFilter = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (role.equals("DOCENTE", ignoreCase = true)) {
                // Vista docente: la existente (editable)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(course.students) { student ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.name, style = MaterialTheme.typography.titleMedium)
                                    Text("ID: ${student.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                var expanded by remember { mutableStateOf(false) }
                                val selected = attendanceMap[student.id] ?: AttendanceStatus.PRESENTE
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text(selected.name)
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        AttendanceStatus.entries.forEach { status ->
                                            DropdownMenuItem(text = { Text(status.name) }, onClick = {
                                                attendanceMap[student.id] = status
                                                expanded = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                course.students.forEach { attendanceMap[it.id] = AttendanceStatus.PRESENTE }
                            }) { Text("Marcar todo como PRESENTE") }

                            Button(onClick = {
                                saving = true
                                val mapToSave = course.students.associate { s -> s.id to (attendanceMap[s.id]?.name ?: AttendanceStatus.PRESENTE.name) }
                                scope.launch {
                                    try {
                                        val attendanceDoc = hashMapOf(
                                            "courseId" to course.id,
                                            "courseName" to course.name,
                                            "date" to com.google.firebase.Timestamp.now(),
                                            "teacherId" to (auth.currentUser?.uid ?: ""),
                                            "records" to mapToSave
                                        )
                                        firestore.collection("attendances").add(attendanceDoc).await()
                                        Toast.makeText(context, "Asistencia guardada", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error guardando: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        saving = false
                                    }
                                }
                            }, enabled = !saving) {
                                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Guardar asistencia")
                            }
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                }
            } else {
                // Vista estudiante: leer registros de asistencias para este curso y mostrar solo-lectura
                var attendanceRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
                LaunchedEffect(course, cutoffDays) {
                     attendanceRecords = emptyList()
                     try {
                         val baseQuery = firestore.collection("attendances").whereEqualTo("courseId", course.id)
                         val query = if (cutoffDays != null) {
                             val cal = Calendar.getInstance()
                             val days = cutoffDays ?: 0
                             cal.add(Calendar.DAY_OF_YEAR, -days)
                             val cutoff = com.google.firebase.Timestamp(cal.time)
                             baseQuery.whereGreaterThanOrEqualTo("date", cutoff)
                         } else baseQuery
                         val snaps = query.get().await()
                         val list = mutableListOf<AttendanceRecord>()
                         for (doc in snaps.documents) {
                             val ts = doc.getTimestamp("date")?.toDate() ?: continue
                             val recordsMap = doc.get("records") as? Map<*, *> ?: continue
                             val userId = auth.currentUser?.uid ?: ""
                             val raw = recordsMap[userId] as? String ?: continue
                             val status = when (raw.uppercase()) {
                                 "PRESENTE" -> AttendanceStatus.PRESENTE
                                 "TARDE" -> AttendanceStatus.TARDE
                                 "JUSTIFICADO" -> AttendanceStatus.JUSTIFICADO
                                 else -> AttendanceStatus.AUSENTE
                             }
                             val obs = doc.getString("observations") ?: ""
                             list.add(AttendanceRecord(ts, status, obs))
                         }
                         attendanceRecords = list.sortedByDescending { it.date }
                     } catch (_: Exception) {
                         // ignore
                     }
                 }

                 if (attendanceRecords.isEmpty()) {
                     Text("No se encuentran registros de asistencia para este curso.")
                 } else {
                     LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                         items(attendanceRecords) { rec ->
                             AttendanceRecordItem(rec)
                         }
                     }
                 }
             }
         }
     }
 }

@Composable
fun AttendanceRecordItem(record: AttendanceRecord) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (record.status) {
                    AttendanceStatus.PRESENTE -> Icons.Filled.CheckCircle
                    AttendanceStatus.TARDE -> Icons.Filled.Schedule
                    AttendanceStatus.AUSENTE -> Icons.Filled.Cancel
                    AttendanceStatus.JUSTIFICADO -> Icons.Filled.Task
                },
                contentDescription = null,
                tint = when (record.status) {
                    AttendanceStatus.PRESENTE -> Color(0xFF4CAF50)
                    AttendanceStatus.TARDE -> Color(0xFFFFC107)
                    AttendanceStatus.AUSENTE -> Color(0xFFFF5722)
                    AttendanceStatus.JUSTIFICADO -> Color(0xFF2196F3)
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("EEEE, dd 'de' MMMM yyyy", Locale.getDefault()).format(record.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    record.status.name.replace("_", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (record.status) {
                        AttendanceStatus.PRESENTE -> Color(0xFF4CAF50)
                        AttendanceStatus.TARDE -> Color(0xFFFFC107)
                        AttendanceStatus.AUSENTE -> Color(0xFFFF5722)
                        AttendanceStatus.JUSTIFICADO -> Color(0xFF2196F3)
                    },
                    fontWeight = FontWeight.SemiBold
                )
                if (record.observations.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        record.observations,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
