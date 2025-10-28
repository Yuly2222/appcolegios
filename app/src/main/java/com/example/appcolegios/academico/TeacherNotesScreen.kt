@file:Suppress("unused", "UNUSED_PARAMETER", "RedundantCallOnCollection")

package com.example.appcolegios.academico

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
import com.google.firebase.firestore.SetOptions

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit

data class CourseInfo(val id: String, val name: String)
data class StudentInfo(val id: String, val name: String?, val email: String?, val weightedGrade: Double?)

@Suppress("UNUSED_PARAMETER")
@Composable
fun TeacherNotesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var loading by remember { mutableStateOf(true) }
    var courses by remember { mutableStateOf<List<CourseInfo>>(emptyList()) }
    var studentsByCourse by remember { mutableStateOf<Map<String, List<StudentInfo>>>(emptyMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Actividades por curso
    var activitiesByCourse by remember { mutableStateOf<Map<String, List<Pair<String, Double>>>>(emptyMap()) }

    // Estados para manejar diálogos de actividades y edición por estudiante
    var managingActivitiesFor by remember { mutableStateOf<CourseInfo?>(null) }
    var addingActivityName by remember { mutableStateOf("") }
    var addingActivityWeight by remember { mutableStateOf(1.0) }

    var editingStudentGradesFor by remember { mutableStateOf<Pair<CourseInfo, StudentInfo>?>(null) }
    var gradeInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // activityId -> grade string

    var editingPair by remember { mutableStateOf<Pair<CourseInfo, StudentInfo>?>(null) }
    var newGradeText by remember { mutableStateOf("") }
    var isSavingGrade by remember { mutableStateOf(false) }

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

                // Construir lista de cursos explicitamente para evitar advertencias del analizador
                val loadedCoursesList = mutableListOf<CourseInfo>()
                for (doc in courseDocs) {
                    val name = doc.getString("name") ?: doc.getString("title") ?: doc.id
                    loadedCoursesList.add(CourseInfo(id = doc.id, name = name))
                }
                val loadedCourses = loadedCoursesList.toList()

                val tmpMap = mutableMapOf<String, List<StudentInfo>>()
                val tmpActivities = mutableMapOf<String, List<Pair<String, Double>>>()

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

                        // Cargar actividades del curso (subcoleccion 'activities')
                        try {
                            val actsSnap = firestore.collection("courses").document(course.id).collection("activities").get().await()
                            val acts = actsSnap.documents.mapNotNull { a ->
                                val aid = a.id
                                val weight = (a.getDouble("weight") ?: (a.get("weight") as? Number)?.toDouble() ?: 0.0)
                                aid to weight
                            }
                            tmpActivities[course.id] = acts
                        } catch (_: Exception) {
                            tmpActivities[course.id] = emptyList()
                        }
                    } catch (_: Exception) {
                        tmpMap[course.id] = emptyList()
                        tmpActivities[course.id] = emptyList()
                    }
                }

                courses = loadedCourses
                studentsByCourse = tmpMap
                activitiesByCourse = tmpActivities
                loading = false
            } catch (_: Exception) {
                errorMsg = "Error al cargar datos"
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
                            // Botón para gestionar actividades del curso
                            TextButton(onClick = { managingActivitiesFor = course }) { Text("Actividades") }
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
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(s.name ?: s.email ?: s.id, style = MaterialTheme.typography.bodyLarge)
                                        if (!s.email.isNullOrBlank()) Text(s.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            s.weightedGrade?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = {
                                            // Abrir diálogo para editar nota final
                                            editingPair = course to s
                                            newGradeText = s.weightedGrade?.toString() ?: ""
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Editar nota")
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        // Abrir diálogo para editar notas por actividad
                                        IconButton(onClick = {
                                            editingStudentGradesFor = course to s
                                            // preparar inputs a partir de actividades existentes y posibles notas
                                            val acts = activitiesByCourse[course.id] ?: emptyList()
                                            val initial = mutableMapOf<String, String>()
                                            // intentar leer notas actuales del student (si hay), de la lista students tenemos sólo weighted; hacemos fetch
                                            scope.launch {
                                                try {
                                                    val sDocRef = firestore.collection("courses").document(course.id).collection("students").document(s.id)
                                                    val sdoc = sDocRef.get().await()
                                                    val gradesMap = sdoc.get("grades") as? Map<*, *>
                                                    for (a in acts) {
                                                        val aid = a.first
                                                        val existing = (gradesMap?.get(aid) as? Map<*, *>)?.get("grade") as? Number
                                                        initial[aid] = existing?.toString() ?: ""
                                                    }
                                                } catch (_: Exception) {
                                                }
                                                gradeInputs = initial
                                            }
                                        }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Editar por actividad")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogo para gestionar actividades (agregar)
    if (managingActivitiesFor != null) {
        val course = managingActivitiesFor!!
        AlertDialog(onDismissRequest = { managingActivitiesFor = null }, title = { Text("Actividades - ${course.name}") }, text = {
            Column {
                val acts = activitiesByCourse[course.id] ?: emptyList()
                if (acts.isEmpty()) Text("No hay actividades.") else acts.forEach { (aid, weight) -> Text("- $aid (peso: $weight)") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = addingActivityName, onValueChange = { addingActivityName = it }, label = { Text("Nombre actividad") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = addingActivityWeight.toString(), onValueChange = { addingActivityWeight = it.toDoubleOrNull() ?: 1.0 }, label = { Text("Peso (ej. 0.2)") })
            }
        }, confirmButton = {
            TextButton(onClick = {
                // crear actividad en subcolección
                val aid = addingActivityName.ifBlank { "act_${System.currentTimeMillis()}" }
                scope.launch {
                    try {
                        firestore.collection("courses").document(course.id).collection("activities").document(aid)
                            .set(mapOf("name" to addingActivityName, "weight" to addingActivityWeight))
                            .await()
                        // reload activities
                        val actsSnap = firestore.collection("courses").document(course.id).collection("activities").get().await()
                        val newActs = actsSnap.documents.mapNotNull { a ->
                            val aid2 = a.id
                            val weight = (a.getDouble("weight") ?: (a.get("weight") as? Number)?.toDouble() ?: 0.0)
                            aid2 to weight
                        }
                        activitiesByCourse = activitiesByCourse.toMutableMap().also { it[course.id] = newActs }
                    } catch (_: Exception) {}
                    addingActivityName = ""
                    addingActivityWeight = 1.0
                    managingActivitiesFor = null
                }
            }) { Text("Agregar") }
        }, dismissButton = { TextButton(onClick = { managingActivitiesFor = null }) { Text("Cerrar") } })
    }

    // Dialogo para editar notas por actividad para un estudiante
    if (editingStudentGradesFor != null) {
        val (course, student) = editingStudentGradesFor!!
        val acts = activitiesByCourse[course.id] ?: emptyList()
        AlertDialog(onDismissRequest = { editingStudentGradesFor = null }, title = { Text("Editar notas - ${student.name}") }, text = {
            Column {
                if (acts.isEmpty()) Text("No hay actividades en este curso.")
                acts.forEach { (aid, weight) ->
                    val label = remember(aid) { aid }
                    val value = gradeInputs[aid] ?: ""
                    OutlinedTextField(value = value, onValueChange = { v -> gradeInputs = gradeInputs.toMutableMap().also { it[aid] = v } }, label = { Text("$label (peso $weight)") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                // Guardar las notas en courses/{courseId}/students/{studentId}.grades
                scope.launch {
                    try {
                        val gradesMap = mutableMapOf<String, Any>()
                        for (a in acts) {
                            val aid = a.first
                            val weight = a.second
                            val gradeNum = gradeInputs[aid]?.toDoubleOrNull()
                            if (gradeNum != null) {
                                gradesMap[aid] = mapOf("grade" to gradeNum, "weight" to weight)
                            }
                        }
                        if (gradesMap.isNotEmpty()) {
                            try {
                                firestore.collection("courses").document(course.id).collection("students").document(student.id)
                                    .set(mapOf("grades" to gradesMap), SetOptions.merge()).await()
                                // recargar doc y actualizar estado local
                                val sdoc = firestore.collection("courses").document(course.id).collection("students").document(student.id).get().await()
                                val weighted = calculateWeightedFromDoc(sdoc.data)
                                val updated = studentsByCourse.toMutableMap()
                                val list = updated[course.id]?.map { if (it.id == student.id) it.copy(weightedGrade = weighted) else it } ?: emptyList()
                                updated[course.id] = list
                                studentsByCourse = updated
                            } catch (_: Exception) {
                                // ignore
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    } finally {
                        editingStudentGradesFor = null
                        gradeInputs = emptyMap()
                    }
                }
            }) { Text("Guardar") }
        }, dismissButton = { TextButton(onClick = { editingStudentGradesFor = null }) { Text("Cancelar") } })
    }

    // Diálogo de edición de nota final (individual)
    if (editingPair != null) {
        val (course, student) = editingPair!!
        AlertDialog(
            onDismissRequest = { editingPair = null },
            title = { Text("Editar nota de ${student.name ?: student.id}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newGradeText,
                        onValueChange = { newGradeText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Nota (ej. 4.5)") },
                        singleLine = true
                    )
                    if (isSavingGrade) Spacer(Modifier.height(8.dp))
                    if (isSavingGrade) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = newGradeText.toDoubleOrNull()
                    if (parsed == null) {
                        editingPair = null
                        return@TextButton
                    }
                    isSavingGrade = true
                    scope.launch {
                        try {
                            val targetRef = firestore.collection("courses").document(course.id).collection("students").document(student.id)
                            try {
                                targetRef.set(mapOf("finalGrade" to parsed), SetOptions.merge()).await()
                            } catch (_: Exception) {
                                // fallback: escribir en collection global students/{id}
                                try {
                                    firestore.collection("students").document(student.id).set(mapOf("finalGrade" to parsed), SetOptions.merge()).await()
                                } catch (_: Exception) {}
                            }

                            // actualizar estado local
                            val updated = studentsByCourse.toMutableMap()
                            val list = updated[course.id]?.map { if (it.id == student.id) it.copy(weightedGrade = parsed) else it } ?: emptyList()
                            updated[course.id] = list
                            studentsByCourse = updated
                        } catch (_: Exception) {
                            // ignore
                        } finally {
                            isSavingGrade = false
                            editingPair = null
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingPair = null }) { Text("Cancelar") } }
        )
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
    val numbers = data.values.mapNotNull { (it as? Number)?.toDouble() }
    if (numbers.isNotEmpty()) return numbers.average()

    return null
}
