package com.example.appcolegios.academico

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseSimple>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseSimple?>(null) }
    val scope = rememberCoroutineScope()

    // mensaje
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    // archivos adjuntos (URIs permitidas) - permitir múltiples selecciones
    val attachments = remember { mutableStateListOf<Uri>() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
        uris?.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            attachments.add(uri)
        }
    }

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
            val announcements = remember { mutableStateListOf<AnnouncementPreview>() }
            var selectedAnnouncement by remember { mutableStateOf<AnnouncementPreview?>(null) }

            suspend fun loadAnnouncementsForCourse() {
                try {
                    val snap = firestore.collection("announcements")
                        .whereEqualTo("courseId", course.id)
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get().await()
                    announcements.clear()
                    snap.documents.forEach { d ->
                        val subj = d.getString("subject") ?: ""
                        val bod = d.getString("body") ?: ""
                        val atts = d.get("attachments") as? List<*> ?: emptyList<Any>()
                        val attsStr = atts.mapNotNull { it?.toString() }
                        val ts = d.get("createdAt") as? com.google.firebase.Timestamp
                        announcements.add(AnnouncementPreview(d.id, subj, bod, attsStr, ts))
                    }
                } catch (_: Exception) {
                    // ignore load errors silently for now
                }
            }

            LaunchedEffect(course.id) { loadAnnouncementsForCourse() }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { selectedCourse = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text(course.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            // Nota: los comunicados se enviarán a TODOS los estudiantes del curso
            Text("Se enviará a TODOS los estudiantes del curso.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Asunto") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                maxLines = 12
            )

            Spacer(Modifier.height(8.dp))
            Text("Adjuntos:")
            attachments.forEach { uri ->
                Text(uri.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Adjuntar archivos") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // enviar comunicado
                    if (subject.isBlank() || body.isBlank()) {
                        Toast.makeText(context, "Asunto y mensaje son requeridos", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        try {
                            val doc = hashMapOf<String, Any>(
                                "courseId" to course.id,
                                "courseName" to course.name,
                                "teacherId" to (auth.currentUser?.uid ?: ""),
                                "subject" to subject,
                                "body" to body,
                                "attachments" to attachments.map { it.toString() },
                                // Enviar a todos los estudiantes del curso
                                "recipients" to course.students.map { it.id },
                                "createdAt" to com.google.firebase.Timestamp.now()
                            )
                            val ref = firestore.collection("announcements").add(doc).await()

                            // Crear notificaciones para TODOS los estudiantes del curso
                            course.students.forEach { student ->
                                val studentId = student.id
                                val notif = hashMapOf(
                                    "titulo" to subject,
                                    "cuerpo" to body.take(200),
                                    "remitente" to (auth.currentUser?.email ?: "Profesor"),
                                    "fechaHora" to com.google.firebase.Timestamp.now(),
                                    "leida" to false,
                                    "relatedId" to ref.id,
                                    "type" to "announcement"
                                )
                                firestore.collection("users").document(studentId)
                                    .collection("notifications").add(notif)
                            }

                            Toast.makeText(context, "Comunicado enviado", Toast.LENGTH_SHORT).show()
                            // limpiar
                            subject = ""
                            body = ""
                            attachments.clear()
                            // recargar comunicados
                            loadAnnouncementsForCourse()
                            // Navegar a pantalla de notificaciones (si hay NavController)
                            navController?.navigate(com.example.appcolegios.navigation.AppRoutes.Notifications.route)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error enviando comunicado: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Enviar comunicado") }
            }

            Spacer(Modifier.height(16.dp))

            // Sección: comunicados enviados
            if (announcements.isNotEmpty()) {
                Text("Comunicados enviados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                announcements.forEach { a ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedAnnouncement = a }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(a.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(a.body.take(150), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Diálogo para ver comunicado completo
            if (selectedAnnouncement != null) {
                val a = selectedAnnouncement!!
                AlertDialog(onDismissRequest = { selectedAnnouncement = null }, confirmButton = {
                    TextButton(onClick = { selectedAnnouncement = null }) { Text("Cerrar") }
                }, title = { Text(a.subject) }, text = {
                    Column { Text(a.body); if (a.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp)); Text("Adjuntos:")
                        a.attachments.forEach { t -> Text(t, style = MaterialTheme.typography.bodySmall) }
                    } }
                })
            }
        }
    }
}

// Pequeña representación para mostrar comunicados ya enviados
data class AnnouncementPreview(
    val id: String,
    val subject: String,
    val body: String,
    val attachments: List<String> = emptyList(),
    val createdAt: com.google.firebase.Timestamp? = null
)
