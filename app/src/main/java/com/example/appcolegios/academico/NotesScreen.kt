package com.example.appcolegios.academico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel

data class Grade(
    val subject: String,
    val period: String,
    val grade: Double,
    val observations: String,
    val teacher: String
)

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    // Rol del usuario para decidir comportamiento (estudiante vs padre)
    val userPrefs = remember { UserPreferencesRepository(context) }
    val userData = userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null)).value
    // Preferir rol cargado desde Firestore (ProfileViewModel) para evitar inconsistencia
    val profileVm: ProfileViewModel = viewModel()
    val roleFromDb by profileVm.roleString.collectAsState(initial = null)
    val role = (roleFromDb ?: userData.role ?: "").uppercase(Locale.getDefault())
    val isParent = role == "PADRE" || role == "PARENT"

    // ViewModel para hijos (cuando es padre)
    val children by profileVm.children.collectAsState()

    // Estado común
    var grades by remember { mutableStateOf<List<Grade>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Cuando es padre: soporte para seleccionar hijo
    var showSelectChildDialog by remember { mutableStateOf(false) }
    var selectedChildIndex by remember { mutableStateOf(0) }

    // Determinar studentId objetivo
    val currentUid = auth.currentUser?.uid
    // IMPORTANTE: si el rol es ESTUDIANTE, forzar que el objetivo sea el propio usuario
    val targetStudentId: String? = when {
        role == "ESTUDIANTE" -> currentUid
        isParent -> children.getOrNull(selectedChildIndex)?.id
        else -> currentUid // fallback para docentes/admins: por ahora mostrar datos del usuario si existe
    }

    // Cargar notas desde Firestore para el estudiante objetivo
    LaunchedEffect(targetStudentId) {
        grades = emptyList()
        errorMsg = null
        if (targetStudentId.isNullOrBlank()) return@LaunchedEffect
        loading = true
        try {
            val snaps = firestore.collection("grades")
                .whereEqualTo("studentId", targetStudentId)
                .get()
                .await()
            val list = snaps.documents.map { d ->
                Grade(
                    subject = d.getString("subject") ?: d.getString("materia") ?: "Materia",
                    period = d.getString("period") ?: d.getString("periodo") ?: "-",
                    grade = d.getDouble("grade") ?: (d.get("calificacion") as? Number)?.toDouble() ?: 0.0,
                    observations = d.getString("observations") ?: d.getString("observaciones") ?: "",
                    teacher = d.getString("teacher") ?: d.getString("docente") ?: ""
                )
            }
            grades = list
        } catch (e: Exception) {
            errorMsg = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }

    val averageGrade = if (grades.isEmpty()) 0.0 else grades.map { it.grade }.average()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isParent) Modifier.clickable { showSelectChildDialog = true } else Modifier),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val title = when {
                        isParent -> children.getOrNull(selectedChildIndex)?.nombre ?: "Estudiante"
                        else -> "Mis notas"
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format(Locale.getDefault(), "%.2f", averageGrade),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            averageGrade >= 4.5 -> Color(0xFF4CAF50)
                            averageGrade >= 4.0 -> Color(0xFF2196F3)
                            averageGrade >= 3.5 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5722)
                        }
                    )
                    if (isParent) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Diálogo de selección (solo padres)
        if (isParent && showSelectChildDialog) {
            var sel by remember { mutableStateOf(selectedChildIndex) }
            AlertDialog(
                onDismissRequest = { showSelectChildDialog = false },
                title = { Text("Selecciona estudiante") },
                text = {
                    Column {
                        if (children.isEmpty()) {
                            Text("No hay hijos asociados")
                        } else {
                            children.forEachIndexed { idx, ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { sel = idx },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = sel == idx, onClick = { sel = idx })
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(ch.nombre, fontWeight = FontWeight.SemiBold)
                                        Text("Curso: ${ch.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (children.isNotEmpty()) selectedChildIndex = sel
                        showSelectChildDialog = false
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Notas por Materia",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            errorMsg != null -> {
                Text(
                    text = "Error: ${errorMsg}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(grades) { grade ->
                        GradeCard(grade)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeCard(grade: Grade) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        grade.subject,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        grade.period,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        grade.grade >= 4.5 -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        grade.grade >= 4.0 -> Color(0xFF2196F3).copy(alpha = 0.2f)
                        grade.grade >= 3.5 -> Color(0xFFFFC107).copy(alpha = 0.2f)
                        else -> Color(0xFFFF5722).copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        String.format(Locale.getDefault(), "%.1f", grade.grade),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            grade.grade >= 4.5 -> Color(0xFF4CAF50)
                            grade.grade >= 4.0 -> Color(0xFF2196F3)
                            grade.grade >= 3.5 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5722)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Observaciones:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        grade.observations,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Prof. ${grade.teacher}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
