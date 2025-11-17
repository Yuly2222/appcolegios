package com.example.appcolegios.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.appcolegios.navigation.AppRoutes

// Nuevos imports para Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import com.example.appcolegios.R

data class TeacherDashboardState(
    val teacherName: String = "",
    val assignedCourses: List<CourseInfo> = emptyList(),
    val pendingGrades: Int = 0,
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
fun TeacherHomeScreen(navController: NavController, displayName: String? = null) {
    var state by remember { mutableStateOf(TeacherDashboardState()) }
    // Capturar contexto composable para uso seguro dentro de coroutines
    val context = LocalContext.current

    LaunchedEffect(displayName) {
        // Usamos el uid actual si está disponible para intentar cargar nombre desde Firestore
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val fetchedState = loadTeacherDashboardData(uid)
        // Determinar el nombre final: preferir displayName > fetchedState > string resource
        val teacherNameToUse = displayName?.takeIf { it.isNotBlank() }
            ?: fetchedState.teacherName.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.role_teacher)
        state = fetchedState.copy(teacherName = teacherNameToUse)
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header del profesor
            item {
                TeacherInfoCard(
                    teacherName = state.teacherName,
                    coursesCount = state.assignedCourses.size
                )
            }

            // Acciones rápidas
            item {
                QuickActionsCard(navController)
            }

            // Clases de hoy
            if (state.todayClasses.isNotEmpty()) {
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
            }

            // Cursos asignados
            if (state.assignedCourses.isNotEmpty()) {
                item {
                    Text(
                        "Mis Cursos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.assignedCourses) { course ->
                    CourseCard(course)
                }
            }

            // Actividades recientes
            if (state.recentActivities.isNotEmpty()) {
                item {
                    Text(
                        "Actividades Publicadas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.recentActivities) { activity ->
                    ActivityCard(activity)
                }
            }
        }
    }
}

@Composable
private fun TeacherInfoCard(teacherName: String, coursesCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
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
                    teacherName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$coursesCount cursos asignados",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun QuickActionsCard(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Acciones Rápidas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Filled.CheckCircle,
                    label = "Asistencia",
                    onClick = { navController.navigate(AppRoutes.Attendance.route) }
                )

                QuickActionButton(
                    icon = Icons.Filled.Grade,
                    label = "Calificar",
                    onClick = { navController.navigate(AppRoutes.Grading.route) }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    label = "Tarea",
                    onClick = { navController.navigate(AppRoutes.Tasks.route) }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Comunicado",
                    onClick = { navController.navigate(AppRoutes.Notifications.route) }
                )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ClassCard(classInfo: ClassInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    classInfo.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Curso: ${classInfo.course}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Salón: ${classInfo.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                classInfo.time,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CourseCard(course: CourseInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    course.subject,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "${course.studentsCount} estudiantes",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: ActivityInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                when (activity.type) {
                    "TAREA" -> Icons.AutoMirrored.Filled.Assignment
                    "EXAMEN" -> Icons.Filled.Quiz
                    else -> Icons.AutoMirrored.Filled.Article
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${activity.type} - ${activity.course}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Entrega: ${activity.dueDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Ahora la función intenta obtener el nombre del docente desde Firestore usando el uid; si falla, mantiene el fallback estático.
suspend fun loadTeacherDashboardData(userId: String?): TeacherDashboardState {
    // Datos de fallback vacíos (sin ejemplos estáticos)
    val fallback = TeacherDashboardState(
        teacherName = "",
        assignedCourses = emptyList(),
        pendingGrades = 0,
        todayClasses = emptyList(),
        recentActivities = emptyList(),
        loading = false
    )

    // Intentamos leer el documento del docente en Firestore si tenemos uid
    if (userId.isNullOrBlank()) {
        return fallback
    }

    return try {
        val doc = FirebaseFirestore.getInstance()
            .collection("teachers")
            .document(userId)
            .get()
            .await()

        if (!doc.exists()) {
            fallback
        } else {
            // Intentar mapear listas desde el documento si están presentes
            val assignedCourses = mutableListOf<CourseInfo>()
            (doc.get("assignedCourses") as? List<*>)?.forEach { item ->
                val m = item as? Map<*,*>
                val name = (m?.get("name") as? String) ?: (m?.get("curso") as? String) ?: ""
                val studentsCount = when (val sc = m?.get("studentsCount")) {
                    is Long -> sc.toInt()
                    is Int -> sc
                    is Double -> sc.toInt()
                    else -> 0
                }
                val subject = (m?.get("subject") as? String) ?: (m?.get("materia") as? String) ?: ""
                if (name.isNotBlank()) assignedCourses.add(CourseInfo(name, studentsCount, subject))
            }

            val todayClasses = mutableListOf<ClassInfo>()
            (doc.get("todayClasses") as? List<*>)?.forEach { item ->
                val m = item as? Map<*,*>
                val subject = (m?.get("subject") as? String) ?: ""
                val course = (m?.get("course") as? String) ?: (m?.get("curso") as? String) ?: ""
                val time = (m?.get("time") as? String) ?: (m?.get("hora") as? String) ?: ""
                val room = (m?.get("room") as? String) ?: (m?.get("salon") as? String) ?: ""
                if (subject.isNotBlank() || course.isNotBlank()) todayClasses.add(ClassInfo(subject, course, time, room))
            }

            val recentActivities = mutableListOf<ActivityInfo>()
            (doc.get("recentActivities") as? List<*>)?.forEach { item ->
                val m = item as? Map<*,*>
                val title = (m?.get("title") as? String) ?: (m?.get("titulo") as? String) ?: ""
                val type = (m?.get("type") as? String) ?: (m?.get("tipo") as? String) ?: ""
                val course = (m?.get("course") as? String) ?: (m?.get("curso") as? String) ?: ""
                val dueDate = (m?.get("dueDate") as? String) ?: (m?.get("fecha") as? String) ?: ""
                if (title.isNotBlank()) recentActivities.add(ActivityInfo(title, type, course, dueDate))
            }

            val nameFromFs = (doc.getString("displayName") ?: doc.getString("name"))
            TeacherDashboardState(
                teacherName = nameFromFs ?: "",
                assignedCourses = assignedCourses,
                pendingGrades = (doc.getLong("pendingGrades")?.toInt() ?: doc.getString("pendingGrades")?.toIntOrNull() ?: 0),
                todayClasses = todayClasses,
                recentActivities = recentActivities,
                loading = false
            )
        }
    } catch (e: Exception) {
        // En caso de error de red o permisos devolvemos el fallback
        fallback
    }
}
