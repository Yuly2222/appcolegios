package com.example.appcolegios.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

data class ParentDashboardState(
    val studentName: String = "",
    val studentGrade: String = "",
    val recentGrades: List<Grade> = emptyList(),
    val pendingPayments: List<Payment> = emptyList(),
    val attendancePercentage: Double = 0.0,
    val pendingTasks: Int = 0,
    val notifications: List<ParentNotification> = emptyList(),
    val loading: Boolean = true
)

data class Grade(
    val subject: String,
    val grade: Double,
    val period: String
)

data class Payment(
    val concept: String,
    val amount: Double,
    val dueDate: String,
    val status: String
)

data class ParentNotification(
    val title: String,
    val message: String,
    val date: String,
    val read: Boolean
)

@Composable
fun ParentHomeScreen() {
    var state by remember { mutableStateOf(ParentDashboardState()) }

    LaunchedEffect(Unit) {
        // Cargar datos del padre y estudiante
        state = loadParentDashboardData()
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
            // Header con info del estudiante
            item {
                StudentInfoCard(
                    studentName = state.studentName,
                    grade = state.studentGrade
                )
            }

            // Resumen académico
            item {
                AcademicSummaryCard(
                    attendancePercentage = state.attendancePercentage,
                    pendingTasks = state.pendingTasks,
                    averageGrade = state.recentGrades.map { it.grade }.average()
                )
            }

            // Notas recientes
            item {
                Text(
                    "Notas Recientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.recentGrades) { grade ->
                GradeCard(grade)
            }

            // Pagos pendientes
            item {
                Text(
                    "Pagos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.pendingPayments) { payment ->
                PaymentCard(payment)
            }

            // Notificaciones
            item {
                Text(
                    "Notificaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.notifications.take(3)) { notification ->
                NotificationCard(notification)
            }
        }
    }
}

@Composable
private fun StudentInfoCard(studentName: String, grade: String) {
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
                Icons.Filled.Person,
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
                    "Curso: $grade",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AcademicSummaryCard(
    attendancePercentage: Double,
    pendingTasks: Int,
    averageGrade: Double
) {
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
                "Resumen Académico",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    icon = Icons.Filled.CheckCircle,
                    label = "Asistencia",
                    value = "${attendancePercentage.toInt()}%",
                    color = if (attendancePercentage >= 80) Color(0xFF4CAF50) else Color(0xFFFFC107)
                )

                SummaryItem(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    label = "Tareas Pendientes",
                    value = "$pendingTasks",
                    color = if (pendingTasks == 0) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )

                SummaryItem(
                    icon = Icons.Filled.Star,
                    label = "Promedio",
                    value = String.format(java.util.Locale.getDefault(), "%.1f", averageGrade),
                    color = Color(0xFF2196F3)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
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
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GradeCard(grade: Grade) {
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
                    grade.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Periodo ${grade.period}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                String.format(Locale.getDefault(), "%.1f", grade.grade),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    grade.grade >= 4.5 -> Color(0xFF4CAF50)
                    grade.grade >= 3.5 -> Color(0xFF2196F3)
                    else -> Color(0xFFFF5722)
                }
            )
        }
    }
}

@Composable
private fun PaymentCard(payment: Payment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (payment.status == "PENDIENTE")
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
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
                    payment.concept,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Vence: ${payment.dueDate}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${String.format(Locale.getDefault(), "%,.0f", payment.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    payment.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (payment.status == "PAGADO")
                        Color(0xFF4CAF50)
                    else
                        Color(0xFFFF5722)
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: ParentNotification) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    notification.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun loadParentDashboardData(): ParentDashboardState {
    return try {
        // Datos de ejemplo - en producción se cargarían de Firestore
        ParentDashboardState(
            studentName = "Juan Camilo Díaz",
            studentGrade = "10-A",
            recentGrades = listOf(
                Grade("Matemáticas", 4.5, "1"),
                Grade("Español", 4.2, "1"),
                Grade("Ciencias", 4.8, "1"),
                Grade("Inglés", 4.0, "1")
            ),
            pendingPayments = listOf(
                Payment("Pensión Febrero", 500000.0, "2025-02-28", "PENDIENTE"),
                Payment("Pensión Enero", 500000.0, "2025-01-31", "PAGADO")
            ),
            attendancePercentage = 92.5,
            pendingTasks = 2,
            notifications = listOf(
                ParentNotification(
                    "Reunión de padres",
                    "Reunión este viernes 20 de enero a las 6:00 PM",
                    "Hace 2 días",
                    false
                ),
                ParentNotification(
                    "Calificaciones disponibles",
                    "Las notas del primer periodo ya están disponibles",
                    "Hace 5 días",
                    true
                )
            ),
            loading = false
        )
    } catch (_: Exception) {
        ParentDashboardState(loading = false)
    }
}
