package com.example.appcolegios.academico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel
import com.example.appcolegios.data.model.Student

data class Grade(
    val subject: String,
    val period: String,
    val grade: Double,
    val observations: String,
    val teacher: String
)

// Datos básicos por hijo para este screen
data class ChildStudent(
    val studentName: String,
    val studentCourse: String,
    val grades: List<Grade>
)

@Composable
fun NotesScreen() {
    // Usar ProfileViewModel para obtener hijos reales y determinar si es PADRE
    val profileVm: ProfileViewModel = viewModel()
    val children by profileVm.children.collectAsState()
    val context = LocalContext.current
    val userPrefs = UserPreferencesRepository(context)
    val userData by userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val isStudent = (userData.role ?: "").equals("ESTUDIANTE", ignoreCase = true)

    var selectedChildIndex by remember { mutableStateOf(0) }
    var showSelectChildDialog by remember { mutableStateOf(false) }

    // Si es estudiante y no hay hijos listados, no seleccionar
    LaunchedEffect(children, userData) {
        if (children.isNotEmpty()) selectedChildIndex = 0
    }

    // Mapear datos: usamos datos reales de children si existen; si no, mostramos demo
    val demoGrades = listOf(
        Grade("Matemáticas", "Periodo 1", 4.5, "Excelente desempeño en cálculo", "Herman González"),
        Grade("Español", "Periodo 1", 4.2, "Buena comprensión lectora", "María López"),
        Grade("Ciencias", "Periodo 1", 4.8, "Sobresaliente en laboratorios", "Carlos Ruiz"),
        Grade("Inglés", "Periodo 1", 4.0, "Buen nivel conversacional", "Ana Smith")
    )

    val grades = if (children.isNotEmpty()) {
        // aquí podrías cargar notas reales desde Firestore por studentId; por ahora mostramos demo por hijo
        demoGrades
    } else demoGrades

    val averageGrade = if (grades.isEmpty()) 0.0 else grades.map { it.grade }.average()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con nombre del estudiante y promedio (clickable para seleccionar hijo)
        val currentChild = children.getOrNull(selectedChildIndex)
        Card(
            modifier = if (isStudent) Modifier.fillMaxWidth() else Modifier
                .fillMaxWidth()
                .clickable { showSelectChildDialog = true },
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
                    Text(
                        currentChild?.nombre ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Curso: ${currentChild?.curso ?: "--"}",
                        style = MaterialTheme.typography.bodyMedium,
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
                    // Mostrar drop-down solo si NO es estudiante (para indicar que se puede cambiar)
                    if (!isStudent) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Diálogo de selección de hijo (solo para padres/docentes)
        if (!isStudent && showSelectChildDialog) {
            var sel by remember { mutableStateOf(selectedChildIndex) }
            AlertDialog(onDismissRequest = { showSelectChildDialog = false }, title = { Text("Selecciona estudiante") }, text = {
                Column {
                    if (children.isEmpty()) Text("No hay hijos asociados") else children.forEachIndexed { idx, ch ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { sel = idx }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel == idx, onClick = { sel = idx })
                            Spacer(Modifier.width(8.dp))
                            Column { Text(ch.nombre, fontWeight = FontWeight.SemiBold); Text("Curso: ${ch.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selectedChildIndex = sel; showSelectChildDialog = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") } })
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Notas por Materia",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(grades) { grade ->
                GradeCard(grade)
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

                // Nota con color según el valor
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
