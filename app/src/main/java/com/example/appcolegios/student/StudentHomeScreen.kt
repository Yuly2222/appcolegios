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
import com.example.appcolegios.data.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth

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

// Acción rápida dinámica cargada desde Firebase
data class HomeAction(
    val label: String,
    val route: String,
    val iconKey: String? = null // opcional, para mapear a un icono local
)

@Composable
fun StudentHomeScreen(navController: NavController, displayName: String? = null) {
    var state by remember { mutableStateOf(StudentDashboardState()) }
    var loadedActions by remember { mutableStateOf<List<HomeAction>>(emptyList()) }

    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val storedUser by userPrefs.userData.collectAsState(initial = UserData(null, null, null))
    val repo = remember { FirestoreRepository() }

    LaunchedEffect(displayName, storedUser) {
        // Usar el helper central para resolver (y cachear) el nombre, así compartimos la misma lógica que PADRE
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        // 1) Priorizar displayName explícito (param), luego la preferencia almacenada
        var resolvedNameNullable: String? = when {
            !displayName.isNullOrBlank() -> displayName
            !storedUser.name.isNullOrBlank() -> storedUser.name
            else -> null
        }

        // 2) Si aún no tenemos nombre, intentar leer del nodo `students` en Firestore
        if (resolvedNameNullable.isNullOrBlank()) {
            try {
                if (!uid.isNullOrBlank()) {
                    val studentDoc = repo.getDocumentData("students", uid)
                    val sname = studentDoc?.get("fullName") as? String ?: studentDoc?.get("name") as? String ?: studentDoc?.get("nombre") as? String
                    if (!sname.isNullOrBlank()) resolvedNameNullable = sname
                } else {
                    val email = auth.currentUser?.email
                    if (!email.isNullOrBlank()) {
                        val q = repo.queryWhereEqual("students", "email", email)
                        if (q.isNotEmpty()) {
                            val doc = q[0]
                            val sname = doc["fullName"] as? String ?: doc["name"] as? String ?: doc["nombre"] as? String
                            if (!sname.isNullOrBlank()) resolvedNameNullable = sname
                        }
                    }
                }
            } catch (_: Exception) {
                // no interrumpimos el flujo por un error de lectura del nombre; simplemente ignoramos y usamos fallback
            }
        }

        // 3) Finalmente, fallback a displayName del auth o "--" si todavía no hay valor
        val resolvedName = resolvedNameNullable ?: auth.currentUser?.displayName ?: "--"

        // Cargar cursos reales desde Firestore si existe userId
        // db/auth variables ya inicializadas arriba si es necesario
        val coursesList = mutableListOf<CourseInfo>()
        val classesToday = mutableListOf<ClassInfo>()
        val activities = mutableListOf<ActivityInfo>()
        try {
            if (!uid.isNullOrBlank()) {
                val studentDoc = repo.getDocumentData("students", uid)
                val curso = studentDoc?.get("curso") as? String ?: studentDoc?.get("groupId") as? String ?: ""
                val courseIds = (studentDoc?.get("courses") as? List<*>) ?: (studentDoc?.get("groups") as? List<*>)
                if (!courseIds.isNullOrEmpty()) {
                    for (cid in courseIds) {
                        val cIdStr = cid.toString()
                        val cdoc = repo.getDocumentData("courses", cIdStr)
                        if (cdoc != null) {
                            val name = cdoc["name"] as? String ?: "Curso"
                            val subject = cdoc["subject"] as? String ?: cdoc["materia"] as? String ?: ""
                            val studentsSnap = repo.getSubcollectionDocuments("courses", cIdStr, "students")
                            val count = studentsSnap.size
                            coursesList.add(CourseInfo(name, count, subject))
                        }
                    }
                } else if (curso.isNotBlank()) {
                    coursesList.add(CourseInfo(curso, 0, ""))
                }
            }
        } catch (_: Exception) {
            // ignorar errores de carga
        }

        // Cargar acciones rápidas desde Firestore: colección 'home_actions', campo 'role' == 'student'
        val actions = mutableListOf<HomeAction>()
        try {
            val actionsDocs = repo.queryWhereEqual("home_actions", "role", "student")
            for (doc in actionsDocs) {
                val label = doc["label"] as? String ?: continue
                val route = doc["route"] as? String ?: continue
                val iconKey = doc["iconKey"] as? String
                actions.add(HomeAction(label = label, route = route, iconKey = iconKey))
            }
        } catch (_: Exception) { /* ignore and keep empty */ }

        state = StudentDashboardState(
            studentName = resolvedName,
            enrolledCourses = coursesList,
            todayClasses = classesToday,
            recentActivities = activities,
            loading = false
        )
        // publicar acciones en el View (usar estado local simple si se desea)
        // Si no hubo acciones en Firestore, restaurar acciones por defecto para estudiantes
        val roleLower = storedUser.role?.lowercase()
        loadedActions = if (actions.isNotEmpty()) actions else if (roleLower == "estudiante" || roleLower == "student") {
            listOf(
                HomeAction("Asistencia", "attendance", "attendance"),
                HomeAction("Mensajes", "messages", "messages"),
                HomeAction("Tareas", "tasks", "tasks")
            )
        } else emptyList()
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
            // Enviar acciones dinámicas. Si no hay acciones cargadas, mostrar mensaje en lugar de usar fallback
            val actionsToShow = loadedActions
            StudentQuickActionsCard(navController = navController, actions = actionsToShow)
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
private fun StudentQuickActionsCard(navController: NavController, actions: List<HomeAction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Acciones Rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (actions.isEmpty()) {
                Text("No hay acciones rápidas disponibles.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    actions.forEach { act ->
                        val icon = when (act.iconKey ?: act.label.lowercase()) {
                            "attendance", "asistencia" -> Icons.Filled.CheckCircle
                            "messages", "mensajes" -> Icons.AutoMirrored.Filled.Message
                            "tasks", "tareas" -> Icons.AutoMirrored.Filled.Assignment
                            else -> Icons.Filled.Info
                        }
                        QuickActionButton(icon = icon, label = act.label) {
                            if (act.route.isNotBlank()) navController.navigate(act.route)
                        }
                    }
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
