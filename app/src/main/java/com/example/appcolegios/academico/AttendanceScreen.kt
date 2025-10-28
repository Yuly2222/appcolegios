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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

enum class AttendanceStatus {
    PRESENTE, AUSENTE, TARDE, JUSTIFICADO
}

data class CourseSimple(val id: String, val name: String, val students: List<StudentSimple>)
data class StudentSimple(val id: String, val name: String)

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

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseSimple>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseSimple?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Estado de asistencia por estudiante
    val attendanceMap = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        errorMsg = null
        try {
            val user = auth.currentUser
            val loadedCourses = mutableListOf<CourseSimple>()
            if (user != null) {
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
                loadedCourses.add(
                    CourseSimple(
                        id = "c2",
                        name = "Historia 201",
                        students = listOf(
                            StudentSimple("s4", "Ana López"),
                            StudentSimple("s5", "Diego Torres")
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { selectedCourse = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCard(course: CourseSimple, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${course.students.size} estudiantes", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAttendanceItem(student: StudentSimple, status: AttendanceStatus, onStatusChange: (AttendanceStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // Mostrar también el nombre del estudiante para usar el parámetro y dar contexto
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(student.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            TextButton(onClick = { expanded = true }) {
                Text(status.name)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AttendanceStatus.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = {
                    onStatusChange(s)
                    expanded = false
                })
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
