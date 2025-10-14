package com.example.appcolegios.academico

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: Date,
    val type: EventType
)

enum class EventType {
    CLASE, EXAMEN, TAREA, EVENTO
}

@Composable
fun CalendarScreen() {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showAddEventDialog by remember { mutableStateOf(false) }

    // Eventos de ejemplo para el estudiante
    val events = remember {
        listOf(
            CalendarEvent(
                "1",
                "Examen de Matemáticas",
                "Capítulos 1-5",
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 2) }.time,
                EventType.EXAMEN
            ),
            CalendarEvent(
                "2",
                "Entrega Ensayo",
                "El Quijote - 500 palabras",
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 5) }.time,
                EventType.TAREA
            ),
            CalendarEvent(
                "3",
                "Feria de Ciencias",
                "Presentación de proyecto",
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 8) }.time,
                EventType.EVENTO
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con mes actual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                selectedDate = (selectedDate.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                }
            }) {
                Icon(Icons.Filled.ChevronLeft, "Mes anterior")
            }

            Text(
                text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate.time),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                selectedDate = (selectedDate.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
            }) {
                Icon(Icons.Filled.ChevronRight, "Mes siguiente")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Días de la semana
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Grid del calendario
        CalendarGrid(
            selectedDate = selectedDate,
            events = events,
            onDateSelected = { date ->
                selectedDate = date
            }
        )

        Spacer(Modifier.height(16.dp))

        // Botón para agregar evento
        Button(
            onClick = { showAddEventDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, "Agregar evento")
            Spacer(Modifier.width(8.dp))
            Text("Agregar Evento")
        }

        Spacer(Modifier.height(16.dp))

        // Lista de eventos
        Text(
            "Próximos Eventos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events.sortedBy { it.date }) { event ->
                EventCard(event)
            }
        }
    }

    if (showAddEventDialog) {
        AlertDialog(
            onDismissRequest = { showAddEventDialog = false },
            title = { Text("Agregar Evento") },
            text = { Text("Funcionalidad disponible próximamente") },
            confirmButton = {
                TextButton(onClick = { showAddEventDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun CalendarGrid(
    selectedDate: Calendar,
    events: List<CalendarEvent>,
    onDateSelected: (Calendar) -> Unit
) {
    val calendar = selectedDate.clone() as Calendar
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = Calendar.getInstance()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.height(300.dp)
    ) {
        // Espacios en blanco antes del primer día
        items(firstDayOfWeek) {
            Box(modifier = Modifier.aspectRatio(1f))
        }

        // Días del mes
        items(daysInMonth) { day ->
            val dayNumber = day + 1
            val thisDay = (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, dayNumber)
            }

            val hasEvent = events.any { event ->
                val eventCal = Calendar.getInstance().apply { time = event.date }
                eventCal.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR) &&
                eventCal.get(Calendar.MONTH) == thisDay.get(Calendar.MONTH) &&
                eventCal.get(Calendar.DAY_OF_MONTH) == dayNumber
            }

            val isToday = today.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR) &&
                    today.get(Calendar.MONTH) == thisDay.get(Calendar.MONTH) &&
                    today.get(Calendar.DAY_OF_MONTH) == dayNumber

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .background(
                        color = when {
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            hasEvent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        },
                        shape = CircleShape
                    )
                    .clickable { onDateSelected(thisDay) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasEvent) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono según tipo de evento
            Icon(
                imageVector = when (event.type) {
                    EventType.CLASE -> Icons.Filled.School
                    EventType.EXAMEN -> Icons.Filled.Quiz
                    EventType.TAREA -> Icons.AutoMirrored.Filled.Assignment
                    EventType.EVENTO -> Icons.Filled.Event
                },
                contentDescription = null,
                tint = when (event.type) {
                    EventType.CLASE -> Color(0xFF2196F3)
                    EventType.EXAMEN -> Color(0xFFFF5722)
                    EventType.TAREA -> Color(0xFF4CAF50)
                    EventType.EVENTO -> Color(0xFF9C27B0)
                },
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(event.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
