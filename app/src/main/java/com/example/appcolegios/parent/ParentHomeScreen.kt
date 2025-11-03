package com.example.appcolegios.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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

// Restaurar data classes necesarias
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

// Representación de un hijo con los mismos campos de información
data class ChildInfo(
    val studentName: String,
    val studentGrade: String,
    val recentGrades: List<Grade> = emptyList(),
    val pendingPayments: List<Payment> = emptyList(),
    val attendancePercentage: Double = 0.0,
    val pendingTasks: Int = 0,
    val notifications: List<ParentNotification> = emptyList()
)

data class ParentDashboardState(
    val studentName: String = "",
    val studentGrade: String = "",
    val recentGrades: List<Grade> = emptyList(),
    val pendingPayments: List<Payment> = emptyList(),
    val attendancePercentage: Double = 0.0,
    val pendingTasks: Int = 0,
    val notifications: List<ParentNotification> = emptyList(),
    val children: List<ChildInfo> = emptyList(),
    val selectedChildIndex: Int = 0,
    val loading: Boolean = true
)

@Composable
fun ParentHomeScreen() {
    var state by remember { mutableStateOf(ParentDashboardState()) }
    // Control del diálogo de selección de hijo
    var showSelectChildDialog by remember { mutableStateOf(false) }

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
                    grade = state.studentGrade,
                    onClick = { showSelectChildDialog = true }
                )
            }

            // Resumen académico
            item {
                AcademicSummaryCard(
                    attendancePercentage = state.attendancePercentage,
                    pendingTasks = state.pendingTasks,
                    averageGrade = if (state.recentGrades.isEmpty()) 0.0 else state.recentGrades.map { it.grade }.average()
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

        // Diálogo para seleccionar otro hijo (fuera del LazyColumn, en contexto composable)
        if (showSelectChildDialog) {
            val children = state.children
            var selectedIndex by remember { mutableStateOf(state.selectedChildIndex) }
            AlertDialog(
                onDismissRequest = { showSelectChildDialog = false },
                title = { Text("Selecciona estudiante") },
                text = {
                    Column {
                        if (children.isEmpty()) {
                            Text("No hay hijos asociados")
                        } else {
                            children.forEachIndexed { index, child ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedIndex = index }) {
                                    RadioButton(selected = selectedIndex == index, onClick = { selectedIndex = index })
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(child.studentName, fontWeight = FontWeight.SemiBold)
                                        Text("Curso: ${child.studentGrade}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // Al confirmar, actualizamos el estado con los datos del hijo seleccionado
                        if (state.children.isNotEmpty()) {
                            val chosen = state.children[selectedIndex]
                            state = state.copy(
                                studentName = chosen.studentName,
                                studentGrade = chosen.studentGrade,
                                recentGrades = chosen.recentGrades,
                                pendingPayments = chosen.pendingPayments,
                                attendancePercentage = chosen.attendancePercentage,
                                pendingTasks = chosen.pendingTasks,
                                notifications = chosen.notifications,
                                selectedChildIndex = selectedIndex
                            )
                        }
                        showSelectChildDialog = false
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun StudentInfoCard(studentName: String, grade: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    value = String.format(Locale.getDefault(), "%.1f", averageGrade),
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
        val child1 = ChildInfo(
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
                    "Reunión con los docentes de matemáticas",
                    "2025-03-10",
                    false
                )
            )
        )

        val child2 = ChildInfo(
            studentName = "María Gómez",
            studentGrade = "8-B",
            recentGrades = listOf(
                Grade("Matemáticas", 4.0, "1"),
                Grade("Español", 3.8, "1"),
                Grade("Ciencias", 4.1, "1")
            ),
            pendingPayments = listOf(
                Payment("Pensión Marzo", 480000.0, "2025-03-28", "PENDIENTE")
            ),
            attendancePercentage = 88.0,
            pendingTasks = 1,
            notifications = listOf(
                ParentNotification(
                    "Actividad extracurricular",
                    "María fue seleccionada para el coro escolar",
                    "2025-03-05",
                    false
                )
            )
        )

        ParentDashboardState(
            studentName = child1.studentName,
            studentGrade = child1.studentGrade,
            recentGrades = child1.recentGrades,
            pendingPayments = child1.pendingPayments,
            attendancePercentage = child1.attendancePercentage,
            pendingTasks = child1.pendingTasks,
            notifications = child1.notifications,
            children = listOf(child1, child2),
            selectedChildIndex = 0,
            loading = false
        )
    } catch (e: Exception) {
        ParentDashboardState(loading = false)
    }
}
