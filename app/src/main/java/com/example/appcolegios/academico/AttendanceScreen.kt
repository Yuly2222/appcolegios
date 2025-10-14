package com.example.appcolegios.academico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class AttendanceRecord(
    val date: Date,
    val status: AttendanceStatus,
    val observations: String
)

enum class AttendanceStatus {
    PRESENTE, AUSENTE, TARDE, JUSTIFICADO
}

@Composable
fun AttendanceScreen() {
    // Registros de asistencia del estudiante jcamilodiaz7@gmail.com
    val attendanceRecords = remember {
        val calendar = Calendar.getInstance()
        listOf(
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, -10) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.TARDE,
                "Llegó 15 minutos tarde"
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.AUSENTE,
                "Cita médica"
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.JUSTIFICADO,
                "Cita médica justificada con certificado"
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            ),
            AttendanceRecord(
                calendar.apply { add(Calendar.DAY_OF_MONTH, 1) }.time,
                AttendanceStatus.PRESENTE,
                ""
            )
        )
    }

    val totalDays = attendanceRecords.size
    val presentDays = attendanceRecords.count { it.status == AttendanceStatus.PRESENTE }
    val lateDays = attendanceRecords.count { it.status == AttendanceStatus.TARDE }
    val absentDays = attendanceRecords.count { it.status == AttendanceStatus.AUSENTE }
    val justifiedDays = attendanceRecords.count { it.status == AttendanceStatus.JUSTIFICADO }
    val attendancePercentage = ((presentDays + lateDays + justifiedDays).toDouble() / totalDays * 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Resumen de asistencia
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
                    "Porcentaje de Asistencia",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${attendancePercentage.toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        attendancePercentage >= 90 -> Color(0xFF4CAF50)
                        attendancePercentage >= 80 -> Color(0xFF2196F3)
                        attendancePercentage >= 70 -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5722)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Estadísticas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(
                icon = Icons.Filled.CheckCircle,
                label = "Presente",
                value = presentDays.toString(),
                color = Color(0xFF4CAF50)
            )
            StatCard(
                icon = Icons.Filled.Schedule,
                label = "Tarde",
                value = lateDays.toString(),
                color = Color(0xFFFFC107)
            )
            StatCard(
                icon = Icons.Filled.Cancel,
                label = "Ausente",
                value = absentDays.toString(),
                color = Color(0xFFFF5722)
            )
            StatCard(
                icon = Icons.Filled.Task,
                label = "Justificado",
                value = justifiedDays.toString(),
                color = Color(0xFF2196F3)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Historial de Asistencia",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attendanceRecords.sortedByDescending { it.date }) { record ->
                AttendanceRecordCard(record)
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttendanceRecordCard(record: AttendanceRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (record.status) {
                AttendanceStatus.PRESENTE -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                AttendanceStatus.TARDE -> Color(0xFFFFC107).copy(alpha = 0.1f)
                AttendanceStatus.AUSENTE -> Color(0xFFFF5722).copy(alpha = 0.1f)
                AttendanceStatus.JUSTIFICADO -> Color(0xFF2196F3).copy(alpha = 0.1f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (record.status) {
                    AttendanceStatus.PRESENTE -> Icons.Filled.CheckCircle
                    AttendanceStatus.TARDE -> Icons.Filled.Schedule
                    AttendanceStatus.AUSENTE -> Icons.Filled.Cancel
                    AttendanceStatus.JUSTIFICADO -> Icons.Filled.Task
                },
                contentDescription = null,
                tint = when (record.status) {
                    AttendanceStatus.PRESENTE -> Color(0xFF4CAF50)
                    AttendanceStatus.TARDE -> Color(0xFFFFC107)
                    AttendanceStatus.AUSENTE -> Color(0xFFFF5722)
                    AttendanceStatus.JUSTIFICADO -> Color(0xFF2196F3)
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("EEEE, dd 'de' MMMM yyyy", Locale.getDefault()).format(record.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    record.status.name.replace("_", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (record.status) {
                        AttendanceStatus.PRESENTE -> Color(0xFF4CAF50)
                        AttendanceStatus.TARDE -> Color(0xFFFFC107)
                        AttendanceStatus.AUSENTE -> Color(0xFFFF5722)
                        AttendanceStatus.JUSTIFICADO -> Color(0xFF2196F3)
                    },
                    fontWeight = FontWeight.SemiBold
                )
                if (record.observations.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        record.observations,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
