package com.example.appcolegios.academico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class Grade(
    val subject: String,
    val period: String,
    val grade: Double,
    val observations: String,
    val teacher: String
)

@Composable
fun NotesScreen() {
    // Notas del estudiante jcamilodiaz7@gmail.com
    val grades = remember {
        listOf(
            Grade("Matemáticas", "Periodo 1", 4.5, "Excelente desempeño en cálculo", "Herman González"),
            Grade("Español", "Periodo 1", 4.2, "Buena comprensión lectora", "María López"),
            Grade("Ciencias", "Periodo 1", 4.8, "Sobresaliente en laboratorios", "Carlos Ruiz"),
            Grade("Inglés", "Periodo 1", 4.0, "Buen nivel conversacional", "Ana Smith"),
            Grade("Educación Física", "Periodo 1", 4.6, "Excelente participación", "Pedro Gómez"),
            Grade("Sociales", "Periodo 1", 4.3, "Buen análisis histórico", "Laura Martínez")
        )
    }

    val averageGrade = grades.map { it.grade }.average()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con promedio
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Promedio General",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    String.format(Locale.getDefault(), "%.2f", averageGrade),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        averageGrade >= 4.5 -> Color(0xFF4CAF50)
                        averageGrade >= 4.0 -> Color(0xFF2196F3)
                        averageGrade >= 3.5 -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5722)
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            averageGrade >= 4.5 -> Icons.Filled.Star
                            averageGrade >= 4.0 -> Icons.Filled.CheckCircle
                            else -> Icons.AutoMirrored.Filled.TrendingUp
                        },
                        contentDescription = null,
                        tint = when {
                            averageGrade >= 4.5 -> Color(0xFF4CAF50)
                            averageGrade >= 4.0 -> Color(0xFF2196F3)
                            else -> Color(0xFFFFC107)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            averageGrade >= 4.5 -> "Excelente"
                            averageGrade >= 4.0 -> "Sobresaliente"
                            averageGrade >= 3.5 -> "Aceptable"
                            else -> "Necesita mejorar"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
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
