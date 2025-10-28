package com.example.appcolegios.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.compose.runtime.collectAsState
import com.example.appcolegios.data.UserData
import kotlinx.coroutines.tasks.await

// Estado simplificado para el dashboard de estudiante
data class StudentDashboardState(
    val studentName: String = "",
    val enrolledCourses: List<CourseInfo> = emptyList(),
    val todayClasses: List<ClassInfo> = emptyList(),
    val recentActivities: List<ActivityInfo> = emptyList(),
    val loading: Boolean = true
)

data class CourseInfo(
    val name: String,
    val studentsCount: Int,
    val subject: String
)

data class ClassInfo(
    val subject: String,
    val course: String,
    val time: String,
    val room: String
)

data class ActivityInfo(
    val title: String,
    val type: String,
    val course: String,
    val dueDate: String
)

@Composable
fun StudentHomeScreen(navController: NavController, displayName: String? = null) {
    var state by remember { mutableStateOf(StudentDashboardState()) }

    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val storedUser by userPrefs.userData.collectAsState(initial = UserData(null, null, null))

    LaunchedEffect(displayName, storedUser) {
        val nameToUse = displayName?.takeIf { it.isNotBlank() } ?: storedUser.name ?: "Estudiante"
        // Cargar cursos reales desde Firestore si existe userId
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val coursesList = mutableListOf<CourseInfo>()
        val classesToday = mutableListOf<ClassInfo>()
        val activities = mutableListOf<ActivityInfo>()
        try {
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                val studentDoc = db.collection("students").document(uid).get().await()
                val curso = studentDoc.getString("curso") ?: ""
                val courseIds = studentDoc.get("courses") as? List<*>
                if (!courseIds.isNullOrEmpty()) {
                    for (cid in courseIds) {
                        val cdoc = db.collection("courses").document(cid.toString()).get().await()
                        if (cdoc.exists()) {
                            val name = cdoc.getString("name") ?: "Curso"
                            val subject = cdoc.getString("subject") ?: cdoc.getString("materia") ?: ""
                            val studentsSnap = db.collection("courses").document(cdoc.id).collection("students").get().await()
                            val count = studentsSnap.size()
                            coursesList.add(CourseInfo(name, count, subject))
                        }
                    }
                } else if (curso.isNotBlank()) {
                    // Fallback: usar curso en el perfil
                    coursesList.add(CourseInfo(curso, 0, ""))
                }
                // TODO: cargar clases y actividades reales si el esquema existe
            }
        } catch (_: Exception) {
            // ignorar
        }

        state = StudentDashboardState(
            studentName = nameToUse,
            enrolledCourses = coursesList.ifEmpty { listOf(CourseInfo("Matemáticas 101", 30, "Matemáticas")) },
            todayClasses = classesToday.ifEmpty { listOf(ClassInfo("Matemáticas", "A1", "08:00", "Sala 1")) },
            recentActivities = activities.ifEmpty { listOf(ActivityInfo("Tarea 1", "Tarea", "Matemáticas", "2025-11-01")) },
            loading = false
        )
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StudentInfoCard(studentName = state.studentName, coursesCount = state.enrolledCourses.size)
        }

        item {
            StudentQuickActionsCard(navController = navController)
        }

        item {
            Text(
                "Clases de Hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.todayClasses) { classInfo ->
            ClassCard(classInfo)
        }

        item {
            Text(
                "Mi Curso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Mostrar solo el primer curso (si existe)
        val firstCourse = state.enrolledCourses.firstOrNull()
        if (firstCourse != null) {
            item { CourseCard(firstCourse) }
        }

        item {
            Text(
                "Actividades Recientes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.recentActivities) { activity ->
            ActivityCard(activity)
        }
    }
}

@Composable
private fun StudentInfoCard(studentName: String, coursesCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.School,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    studentName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$coursesCount curso(s) inscritos",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StudentQuickActionsCard(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Acciones Rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickActionButton(icon = Icons.Filled.CheckCircle, label = "Asistencia") {
                    // navegar a asistencia
                    navController.navigate("attendance")
                }
                // NO incluir la opción "Calificar" para estudiantes
                QuickActionButton(icon = Icons.AutoMirrored.Filled.Message, label = "Mensajes") {
                    navController.navigate("messages")
                }
                QuickActionButton(icon = Icons.AutoMirrored.Filled.Assignment, label = "Tareas") {
                    navController.navigate("tasks")
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        FilledIconButton(onClick = onClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ClassCard(classInfo: ClassInfo) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(classInfo.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${classInfo.course} • ${classInfo.time}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(classInfo.room, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CourseCard(courseInfo: CourseInfo) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(courseInfo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(courseInfo.subject, style = MaterialTheme.typography.bodyMedium)
            }
            Text("${courseInfo.studentsCount} alumnos", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActivityCard(activityInfo: ActivityInfo) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(activityInfo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(activityInfo.course, style = MaterialTheme.typography.bodyMedium)
            }
            Text(activityInfo.dueDate, style = MaterialTheme.typography.bodySmall)
        }
    }
}
