package com.example.appcolegios.academico

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Usamos los modelos centralizados en Models.kt: CourseSimple y StudentSimple

data class GradeItem(
    val id: String,
    val studentId: String,
    val activity: String,
    val score: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradingScreen() {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseSimple>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseSimple?>(null) }
    var selectedStudent by remember { mutableStateOf<StudentSimple?>(null) }

    var grades by remember { mutableStateOf<List<GradeItem>>(emptyList()) }
    var loadingGrades by remember { mutableStateOf(false) }

    // load courses for teacher
    LaunchedEffect(Unit) {
        loading = true
        errorMsg = null
        try {
            val user = auth.currentUser
            val loaded = mutableListOf<CourseSimple>()
            if (user != null) {
                val q = firestore.collection("courses").whereEqualTo("teacherId", user.uid).get().await()
                if (!q.isEmpty) {
                    for (doc in q.documents) {
                        val id = doc.id
                        val name = doc.getString("name") ?: "Curso"
                        val studsSnap = firestore.collection("courses").document(id).collection("students").get().await()
                        val studs = studsSnap.documents.map { s -> StudentSimple(s.id, s.getString("name") ?: s.getString("displayName") ?: "Alumno") }
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }

        if (selectedCourse == null) {
            Text("Cursos para calificar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(courses) { course ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedCourse = course }) {
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
        } else if (selectedStudent == null) {
            val course = selectedCourse!!
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { selectedCourse = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Estudiantes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(course.students) { s ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedStudent = s }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(s.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            val student = selectedStudent!!
             Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                 IconButton(onClick = { selectedStudent = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                 Text(student.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
             }
            Spacer(Modifier.height(8.dp))

            // load grades when entering student
            LaunchedEffect(student.id) {
                loadingGrades = true
                grades = emptyList()
                try {
                    val snap = firestore.collection("grades")
                        .whereEqualTo("studentId", student.id)
                        .get().await()
                    val list = snap.documents.map { d ->
                        GradeItem(d.id, d.getString("studentId") ?: "", d.getString("activity") ?: "", (d.getDouble("score") ?: d.getLong("score")?.toDouble()) ?: 0.0)
                    }
                    grades = list
                } catch (e: Exception) {
                    Toast.makeText(context, "Error cargando notas: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    loadingGrades = false
                }
            }

            if (loadingGrades) CircularProgressIndicator() else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Notas por actividad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (grades.isEmpty()) Text("No hay notas registradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(grades) { g ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(g.activity, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Puntaje: ${g.score}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            // edit grade
                                            showEditGradeDialog(context, firestore, g) { updated ->
                                                grades = grades.map { if (it.id == updated.id) updated else it }
                                            }
                                        }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                        IconButton(onClick = {
                                            // delete
                                            scope.launch {
                                                try {
                                                    firestore.collection("grades").document(g.id).delete().await()
                                                    grades = grades.filterNot { it.id == g.id }
                                                    Toast.makeText(context, "Nota eliminada", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error eliminando: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                                    }
                                }
                            }
                        }
                    }

                    // add grade button
                    Button(onClick = {
                        showAddGradeDialog(context, firestore, student.id) { new ->
                            grades = grades + new
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Añadir nota") }
                }
            }
        }
    }
}

private fun showAddGradeDialog(context: android.content.Context, firestore: FirebaseFirestore, studentId: String, onAdded: (GradeItem) -> Unit) {
    // Simple AlertDialog using Compose is more complex; use a blocking approach cannot here.
    // We'll implement a coroutine launch to add a sample grade prompt using Android AlertDialog
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        val etActivity = android.widget.EditText(context)
        etActivity.hint = "Actividad"
        val etScore = android.widget.EditText(context)
        etScore.hint = "Puntaje"
        etScore.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(etActivity)
            addView(etScore)
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Nueva nota")
            .setView(layout)
            .setPositiveButton("Añadir") { _, _ ->
                val act = etActivity.text.toString().trim()
                val score = etScore.text.toString().toDoubleOrNull() ?: 0.0
                if (act.isNotEmpty()) {
                    val data = hashMapOf(
                        "studentId" to studentId,
                        "activity" to act,
                        "score" to score,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    // save
                    firestore.collection("grades").add(data).addOnSuccessListener { ref ->
                        val g = GradeItem(ref.id, studentId, act, score)
                        onAdded(g)
                        Toast.makeText(context, "Nota añadida", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Error añadiendo nota: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

private fun showEditGradeDialog(context: android.content.Context, firestore: FirebaseFirestore, grade: GradeItem, onUpdated: (GradeItem) -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        val etActivity = android.widget.EditText(context)
        etActivity.setText(grade.activity)
        val etScore = android.widget.EditText(context)
        etScore.setText(grade.score.toString())
        etScore.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(etActivity)
            addView(etScore)
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Editar nota")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val act = etActivity.text.toString().trim()
                val score = etScore.text.toString().toDoubleOrNull() ?: 0.0
                if (act.isNotEmpty()) {
                    val map = mapOf("activity" to act, "score" to score)
                    firestore.collection("grades").document(grade.id).update(map).addOnSuccessListener {
                        onUpdated(GradeItem(grade.id, grade.studentId, act, score))
                        Toast.makeText(context, "Nota actualizada", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Error actualizando nota: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
