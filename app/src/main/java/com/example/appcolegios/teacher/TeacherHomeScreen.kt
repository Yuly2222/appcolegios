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
fun TeacherHomeScreen() {
    var state by remember { mutableStateOf(TeacherDashboardState()) }

    LaunchedEffect(Unit) {
        state = loadTeacherDashboardData()
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
                QuickActionsCard()
            }

            // Clases de hoy
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

            // Cursos asignados
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

            // Actividades recientes
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
private fun QuickActionsCard() {
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
                    onClick = { /* Navegar a asistencia */ }
                )

                QuickActionButton(
                    icon = Icons.Filled.Grade,
                    label = "Calificar",
                    onClick = { /* Navegar a calificaciones */ }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    label = "Tarea",
                    onClick = { /* Navegar a nueva tarea */ }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Comunicado",
                    onClick = { /* Navegar a mensajes */ }
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

private fun loadTeacherDashboardData(): TeacherDashboardState {
    return TeacherDashboardState(
        teacherName = "Herman González",
        assignedCourses = listOf(
            CourseInfo("10-A", 32, "Matemáticas"),
            CourseInfo("10-B", 28, "Matemáticas"),
            CourseInfo("11-A", 30, "Física")
        ),
        pendingGrades = 15,
        todayClasses = listOf(
            ClassInfo("Matemáticas", "10-A", "07:00", "A-201"),
            ClassInfo("Física", "11-A", "09:00", "LAB-02"),
            ClassInfo("Matemáticas", "10-B", "14:00", "A-105")
        ),
        recentActivities = listOf(
            ActivityInfo("Ejercicios de Álgebra", "TAREA", "10-A", "2025-01-20"),
            ActivityInfo("Quiz Trigonometría", "EXAMEN", "10-B", "2025-01-22"),
            ActivityInfo("Laboratorio de Física", "PRÁCTICA", "11-A", "2025-01-25")
        ),
        loading = false
    )
}
