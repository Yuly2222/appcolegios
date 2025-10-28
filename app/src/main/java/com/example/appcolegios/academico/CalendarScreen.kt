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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.example.appcolegios.auth.LoginActivity
import kotlinx.coroutines.tasks.await

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
    val context = LocalContext.current

    // Eventos de ejemplo eliminado: ahora se cargan desde Firestore
    val events = remember { mutableStateListOf<CalendarEvent>() }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    // Cargar eventos desde Firestore para el usuario
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            try {
                val snaps = db.collection("users").document(userId).collection("events").get().await()
                events.clear()
                for (doc in snaps.documents) {
                    val id = doc.id
                    val title = doc.getString("title") ?: "Evento"
                    val description = doc.getString("description") ?: ""
                    val tsAny = doc.get("date")
                    val ts = when (tsAny) {
                        is com.google.firebase.Timestamp -> tsAny.toDate()
                        is Date -> tsAny
                        else -> null
                    }
                    val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                    if (ts != null) events.add(CalendarEvent(id, title, description, ts, type))
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // Material3: usar SnackbarHostState y Scaffold
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    Spacer(Modifier.height(6.dp))
                    // boton eliminar
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            // eliminar evento
                            val userId = auth.currentUser?.uid ?: return@TextButton
                            db.collection("users").document(userId).collection("events").document(event.id)
                                .delete()
                                .addOnSuccessListener { events.removeAll { it.id == event.id } }
                        }) { Text("Eliminar") }
                    }
                }
            }
        }
    }

    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedDate.time,
            onDismiss = { showAddEventDialog = false },
            onSave = { title, description, date, type ->
                // Guardar en Firestore en users/{uid}/events/{eventId}
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    scope.launch { snackbarHostState.showSnackbar("Debes iniciar sesión para agregar eventos") }
                    return@AddEventDialog
                }
                val db = FirebaseFirestore.getInstance()
                val eventId = UUID.randomUUID().toString()
                val eventData = mapOf(
                    "id" to eventId,
                    "title" to title,
                    "description" to description,
                    "date" to com.google.firebase.Timestamp(date),
                    "type" to type.name,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                db.collection("users").document(userId).collection("events").document(eventId)
                    .set(eventData)
                    .addOnSuccessListener {
                        // Añadir también a la lista local para feedback inmediato
                        events.add(CalendarEvent(eventId, title, description, date, type))
                        showAddEventDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Evento agregado") }
                    }
                    .addOnFailureListener { e ->
                        // Añadir diagnóstico y manejo específico para permisos
                        val projectId = try { com.google.firebase.FirebaseApp.getInstance().options.projectId } catch (_: Exception) { null }
                        val diag = "uid=${userId}, project=${projectId ?: "unknown"}"
                        Log.e("CalendarScreen", "Error saving event: ${e.message} ($diag)", e)
                        scope.launch {
                            val baseMsg = when (e) {
                                is FirebaseFirestoreException -> when (e.code) {
                                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permisos insuficientes para guardar el evento. Asegúrate de iniciar sesión y revisar las reglas de Firestore."
                                    else -> "Error al guardar evento: ${e.message}"
                                }
                                else -> "Error al guardar evento: ${e.message}"
                            }
                            val res = snackbarHostState.showSnackbar(baseMsg, actionLabel = "Iniciar sesión")
                            if (res == SnackbarResult.ActionPerformed) {
                                // Abrir pantalla de login (actividad existente) como fallback
                                try {
                                    context.startActivity(Intent(context, LoginActivity::class.java))
                                } catch (ex: Exception) {
                                    Log.e("CalendarScreen", "No se pudo abrir LoginActivity: ${ex.message}", ex)
                                }
                            }
                        }
                     }
            }
        )
    }
}

@Composable
private fun AddEventDialog(
    initialDate: Date,
    onDismiss: () -> Unit,
    onSave: (String, String, Date, EventType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var type by remember { mutableStateOf(EventType.EVENTO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Evento") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)}")
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        // Avanzar un día (simple picker inline). Para una implementación completa integrar DatePicker
                        val cal = Calendar.getInstance().apply { time = date }
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        date = cal.time
                    }) { Text("Siguiente día") }
                }
                Spacer(Modifier.height(8.dp))
                // Tipo
                Row { EventType.entries.forEach { ev ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        RadioButton(selected = type == ev, onClick = { type = ev })
                        Text(ev.name)
                    }
                }}
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                onSave(title, description, date, type)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
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
