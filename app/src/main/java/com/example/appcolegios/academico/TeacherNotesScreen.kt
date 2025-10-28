package com.example.appcolegios.academico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class CourseInfo(val id: String, val name: String)
data class StudentInfo(val id: String, val name: String?, val email: String?, val weightedGrade: Double?)

@Composable
fun TeacherNotesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var loading by remember { mutableStateOf(true) }
    var courses by remember { mutableStateOf<List<CourseInfo>>(emptyList()) }
    var studentsByCourse by remember { mutableStateOf<Map<String, List<StudentInfo>>>(emptyMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            try {
                val user = auth.currentUser
                val uid = user?.uid
                val email = user?.email

                // Buscar cursos del docente por campos comunes
                val courseDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                if (!uid.isNullOrBlank()) {
                    val q1 = firestore.collection("courses").whereEqualTo("teacherUid", uid).get().await()
                    courseDocs.addAll(q1.documents)
                    if (courseDocs.isEmpty()) {
                        val q2 = firestore.collection("courses").whereEqualTo("teacherId", uid).get().await()
                        courseDocs.addAll(q2.documents)
                    }
                }
                if (courseDocs.isEmpty() && !email.isNullOrBlank()) {
                    val q3 = firestore.collection("courses").whereEqualTo("teacherEmail", email).get().await()
                    courseDocs.addAll(q3.documents)
                }

                // Fallback: si no hay cursos encontrados, intentar traer todos y filtrar localmente (por si el campo usa otro nombre)
                if (courseDocs.isEmpty()) {
                    val all = firestore.collection("courses").get().await()
                    courseDocs.addAll(all.documents)
                }

                val loadedCourses = courseDocs.mapNotNull { doc ->
                    val name = doc.getString("name") ?: doc.getString("title") ?: doc.id
                    CourseInfo(id = doc.id, name = name)
                }

                val tmpMap = mutableMapOf<String, List<StudentInfo>>()

                // Por cada curso, intentar cargar estudiantes y calcular nota ponderada
                for (course in loadedCourses) {
                    try {
                        // Intentar subcolección courses/{courseId}/students
                        val studentsSnap = firestore.collection("courses").document(course.id)
                            .collection("students").get().await()

                        val studentList = if (!studentsSnap.isEmpty) {
                            studentsSnap.documents.map { sDoc ->
                                val name = sDoc.getString("name") ?: sDoc.getString("displayName") ?: sDoc.getString("fullName")
                                val emailS = sDoc.getString("email")
                                val weighted = calculateWeightedFromDoc(sDoc.data)
                                StudentInfo(id = sDoc.id, name = name, email = emailS, weightedGrade = weighted)
                            }
                        } else {
                            // Intentar colección global "students" con campo courseId
                            val alt = firestore.collection("students").whereEqualTo("courseId", course.id).get().await()
                            if (!alt.isEmpty) {
                                alt.documents.map { sDoc ->
                                    val name = sDoc.getString("name") ?: sDoc.getString("displayName") ?: sDoc.getString("fullName")
                                    val emailS = sDoc.getString("email")
                                    val weighted = calculateWeightedFromDoc(sDoc.data)
                                    StudentInfo(id = sDoc.id, name = name, email = emailS, weightedGrade = weighted)
                                }
                            } else {
                                emptyList()
                            }
                        }

                        tmpMap[course.id] = studentList
                    } catch (e: Exception) {
                        tmpMap[course.id] = emptyList()
                    }
                }

                courses = loadedCourses
                studentsByCourse = tmpMap
                loading = false
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: "Error al cargar datos"
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis Cursos", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        if (courses.isEmpty()) {
            Text("No se encontraron cursos asignados.")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(courses) { course ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(course.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val students = studentsByCourse[course.id] ?: emptyList()
                        Text("Estudiantes: ${students.size}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (students.isEmpty()) {
                            Text("No hay estudiantes registrados para este curso.")
                        } else {
                            students.forEach { s ->
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        // Placeholder: puedes navegar a una pantalla de detalle de estudiante
                                    }
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(s.name ?: s.email ?: s.id, style = MaterialTheme.typography.bodyLarge)
                                        if (!s.email.isNullOrBlank()) Text(s.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        s.weightedGrade?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "--",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper que intenta calcular una nota ponderada desde un map (por ejemplo, campo "grades" o campos sueltos)
fun calculateWeightedFromDoc(data: Map<String, Any>?): Double? {
    if (data == null) return null
    // Posible estructura: "grades": { "actividad1": {"grade": 4.5, "weight": 0.3}, ... }
    val gradesAny = data["grades"]
    if (gradesAny is Map<*, *>) {
        var sumWeighted = 0.0
        var sumWeights = 0.0
        for ((_, v) in gradesAny) {
            if (v is Map<*, *>) {
                val grade = (v["grade"] as? Number)?.toDouble()
                val weight = (v["weight"] as? Number)?.toDouble()
                if (grade != null && weight != null) {
                    sumWeighted += grade * weight
                    sumWeights += weight
                }
            }
        }
        return if (sumWeights > 0.0) (sumWeighted / sumWeights) else null
    }

    // Si hay campo finalGrade
    val finalGrade = (data["finalGrade"] as? Number)?.toDouble()
    if (finalGrade != null) return finalGrade

    // Intentar detectar campos numéricos bajo names conocidos
    val possible = listOf("nota", "grade", "final", "promedio")
    val numbers = data.values.mapNotNull { (it as? Number)?.toDouble() }
    if (numbers.isNotEmpty()) return numbers.average()

    return null
}

