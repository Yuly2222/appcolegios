package com.example.appcolegios.academico

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import com.example.appcolegios.data.model.Role

// Generador simple de ocurrencias para recurrencia básica
fun generateOccurrences(startDate: Date, recurrence: String, rangeStart: Date, rangeEnd: Date): List<Date> {
    val res = mutableListOf<Date>()
    val cal = Calendar.getInstance().apply { time = startDate }
    val endRangeCal = Calendar.getInstance().apply { time = rangeEnd }
    val startRangeCal = Calendar.getInstance().apply { time = rangeStart }

    when (recurrence.uppercase(Locale.getDefault())) {
        "DAILY" -> {
            // avanzar hasta >= startRange
            while (cal.before(startRangeCal)) cal.add(Calendar.DAY_OF_MONTH, 1)
            while (!cal.after(endRangeCal)) {
                res.add(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        "WEEKLY" -> {
            while (cal.before(startRangeCal)) cal.add(Calendar.WEEK_OF_YEAR, 1)
            while (!cal.after(endRangeCal)) {
                res.add(cal.time)
                cal.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        "MONTHLY" -> {
            while (cal.before(startRangeCal)) cal.add(Calendar.MONTH, 1)
            while (!cal.after(endRangeCal)) {
                res.add(cal.time)
                cal.add(Calendar.MONTH, 1)
            }
        }
        else -> { /* no soportado */ }
    }

    return res
}

@Composable
fun CalendarGrid(
    displayedMonth: Calendar,
    selectedDay: Calendar?,
    events: List<CalendarEvent>,
    onDateSelected: (Calendar) -> Unit
) {
    val calendar = (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    // determinar primer día de semana según locale/WeekFields
    val firstDowField = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstDayOfWeek = (firstDowField.value - 1) // DayOfWeek.MONDAY.value==1
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
            val thisDay = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNumber) }

            // usar LocalDate para evitar problemas de zona/hora
            val thisLocal = Instant.ofEpochMilli(thisDay.time.time).atZone(ZoneId.systemDefault()).toLocalDate()

            val dayEvents = events.filter { ev ->
                val evLocal = Instant.ofEpochMilli(ev.date.time).atZone(ZoneId.systemDefault()).toLocalDate()
                evLocal == thisLocal
            }
            val hasEvent = dayEvents.isNotEmpty()

            val isToday = Instant.ofEpochMilli(today.time.time).atZone(ZoneId.systemDefault()).toLocalDate() == thisLocal

            val isSelected = selectedDay?.let { sel ->
                val selLocal = Instant.ofEpochMilli(sel.time.time).atZone(ZoneId.systemDefault()).toLocalDate()
                selLocal == thisLocal
            } ?: false

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            hasEvent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
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
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasEvent) {
                        // mostrar hasta 3 puntos de colores según tipos distintos
                        val types = dayEvents.map { it.type }.distinct()
                        val maxDots = 3
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            types.take(maxDots).forEach { t ->
                                val dotColor = when (t) {
                                    EventType.CLASE -> Color(0xFF2196F3)
                                    EventType.EXAMEN -> Color(0xFFF44336)
                                    EventType.TAREA -> Color(0xFFFFC107)
                                    EventType.EVENTO -> Color(0xFF4CAF50)
                                }
                                Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
                                Spacer(Modifier.width(4.dp))
                            }
                            if (types.size > maxDots) {
                                Text("+${types.size - maxDots}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun EventCard(event: CalendarEvent, onEdit: ((CalendarEvent) -> Unit)? = null, onDelete: ((CalendarEvent) -> Unit)? = null) {
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
            // Reemplazado: icono envuelto en un fondo/círculo para crear la "margen en forma de círculo"
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (event.type) {
                        EventType.CLASE -> Icons.Filled.School
                        EventType.EXAMEN -> Icons.Filled.Warning
                        EventType.TAREA -> Icons.Filled.Assignment
                        EventType.EVENTO -> Icons.Filled.EventNote
                    },
                    contentDescription = null,
                    tint = when (event.type) {
                        EventType.CLASE -> Color(0xFF2196F3)
                        EventType.EXAMEN -> Color(0xFFF44336)
                        EventType.TAREA -> Color(0xFFFFC107)
                        EventType.EVENTO -> Color(0xFF4CAF50)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(event.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.description.isNotBlank()) Text(event.description, style = MaterialTheme.typography.bodyMedium)
            }
            // acciones opcionales
            if (onEdit != null) IconButton(onClick = { onEdit(event) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            if (onDelete != null) IconButton(onClick = { onDelete(event) }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
        }
    }
}

@Composable
fun AddEventDialog(
    initialDate: Date,
    onDismiss: () -> Unit,
    onSave: (String, String, Date, EventType, String?) -> Unit,
    role: Role?,
    userCourses: List<Pair<String,String>> = emptyList()
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateCal by remember { mutableStateOf(Calendar.getInstance().apply { time = initialDate }) }
    var type by remember { mutableStateOf(EventType.EVENTO) }
    var targetCourseId by remember { mutableStateOf<String?>(null) }

    fun openDatePicker() {
        val c = dateCal
        android.app.DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert, { _, y, m, d ->
            c.set(Calendar.YEAR, y)
            c.set(Calendar.MONTH, m)
            c.set(Calendar.DAY_OF_MONTH, d)
            dateCal = c
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun openTimePicker() {
        val c = dateCal
        android.app.TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert, { _, hour, minute ->
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0)
            dateCal = c
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    val canAdd = (role == Role.DOCENTE || role == Role.ADMIN)

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
                    Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateCal.time)}")
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { openDatePicker() }) { Text("Seleccionar fecha") }
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hora: ${String.format(Locale.getDefault(), "%02d:%02d", dateCal.get(Calendar.HOUR_OF_DAY), dateCal.get(Calendar.MINUTE))}")
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { openTimePicker() }) { Text("Seleccionar hora") }
                }

                // Tipo de evento: usar dropdown para ahorrar espacio visual
                Spacer(Modifier.height(4.dp))
                var expandedType by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tipo: ")
                    Spacer(Modifier.width(8.dp))
                    Box {
                        TextButton(onClick = { expandedType = true }) { Text(type.name) }
                        DropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                            EventType.entries.forEach { ev ->
                                DropdownMenuItem(text = { Text(ev.name) }, onClick = { type = ev; expandedType = false })
                            }
                        }
                    }
                }

                // Selección de curso solo para docentes
                if (role == Role.DOCENTE) {
                    Spacer(Modifier.height(8.dp))
                    Text("Curso objetivo (opcional):", style = MaterialTheme.typography.labelMedium)
                    // Mostrar como dropdown para ahorrar espacio visual
                    var expandedCourse by remember { mutableStateOf(false) }
                    val selectedCourseLabel = userCourses.find { it.first == targetCourseId }?.second ?: "(ninguno)"
                    Box {
                        TextButton(onClick = { expandedCourse = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedCourseLabel, modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = expandedCourse, onDismissRequest = { expandedCourse = false }) {
                            DropdownMenuItem(text = { Text("(ninguno)") }, onClick = { targetCourseId = null; expandedCourse = false })
                            userCourses.forEach { course ->
                                DropdownMenuItem(text = { Text(course.second) }, onClick = { targetCourseId = course.first; expandedCourse = false })
                            }
                        }
                    }
                }

                // Mensaje si el rol no tiene permisos
                if (!canAdd) {
                    Spacer(Modifier.height(8.dp))
                    Text("No tienes permisos para crear eventos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!canAdd) return@TextButton
                if (title.isBlank()) return@TextButton
                onSave(title, description, dateCal.time, type, targetCourseId)
            }, enabled = canAdd) {
                Text(if (canAdd) "Guardar" else "No permitido")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun EditEventDialog(event: CalendarEvent, onDismiss: () -> Unit, onSave: (CalendarEvent) -> Unit, role: Role?, userCourses: List<Pair<String,String>> = emptyList()) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(event.title) }
    var description by remember { mutableStateOf(event.description) }
    var dateCal by remember { mutableStateOf(Calendar.getInstance().apply { time = event.date }) }
    var type by remember { mutableStateOf(event.type) }
    var targetCourseId by remember { mutableStateOf<String?>(null) }

    // cargar id de curso objetivo si es evento global
    LaunchedEffect(event) {
        targetCourseId = if (event.source == EventSource.GLOBAL) event.courseId else null
    }

    fun openDatePicker() {
        val c = dateCal
        android.app.DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert, { _, y, m, d -> c.set(y, m, d); dateCal = c }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun openTimePicker() {
        val c = dateCal
        android.app.TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert, { _, h, min -> c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min); dateCal = c }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar Evento") }, text = {
        Column {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fecha: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateCal.time)}")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { openDatePicker() }) { Text("Seleccionar fecha") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hora: ${String.format(Locale.getDefault(), "%02d:%02d", dateCal.get(Calendar.HOUR_OF_DAY), dateCal.get(Calendar.MINUTE))}")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { openTimePicker() }) { Text("Seleccionar hora") }
            }
            // Tipo de evento como dropdown
            Spacer(Modifier.height(4.dp))
            var expandedTypeEdit by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tipo: ")
                Spacer(Modifier.width(8.dp))
                Box {
                    TextButton(onClick = { expandedTypeEdit = true }) { Text(type.name) }
                    DropdownMenu(expanded = expandedTypeEdit, onDismissRequest = { expandedTypeEdit = false }) {
                        EventType.entries.forEach { ev ->
                            DropdownMenuItem(text = { Text(ev.name) }, onClick = { type = ev; expandedTypeEdit = false })
                        }
                    }
                }
            }

            // target course selection for teachers
            if (role == Role.DOCENTE) {
                Spacer(Modifier.height(8.dp))
                Text("Curso objetivo (opcional):", style = MaterialTheme.typography.labelMedium)
                // dropdown para selección de curso
                var expandedCourseEdit by remember { mutableStateOf(false) }
                val selectedCourseLabelEdit = userCourses.find { it.first == targetCourseId }?.second ?: "(ninguno)"
                Box {
                    TextButton(onClick = { expandedCourseEdit = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedCourseLabelEdit, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = expandedCourseEdit, onDismissRequest = { expandedCourseEdit = false }) {
                        DropdownMenuItem(text = { Text("(ninguno)") }, onClick = { targetCourseId = null; expandedCourseEdit = false })
                        userCourses.forEach { course ->
                            DropdownMenuItem(text = { Text(course.second) }, onClick = { targetCourseId = course.first; expandedCourseEdit = false })
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            // Construir objeto preservando owner/creator/course
            val newCourseId = if (event.source == EventSource.GLOBAL) (targetCourseId ?: event.courseId) else null
            val updatedEvent = CalendarEvent(
                event.id,
                title,
                description,
                dateCal.time,
                type,
                event.source,
                ownerId = event.ownerId,
                creatorId = event.creatorId,
                courseId = newCourseId
            )
            onSave(updatedEvent)
        }) { Text("Guardar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

// helper: crea notificaciones en users/{id}/notifications
fun createNotifsForIds(ids: List<String>, title: String, body: String, relatedId: String, db: FirebaseFirestore, auth: FirebaseAuth) {
    if (ids.isEmpty()) return
    // fallback sender used if name lookup fails
    val fallbackSender = auth.currentUser?.email ?: auth.currentUser?.uid ?: "Profesor"
    for (sid in ids) {
        try {
            val notif = hashMapOf(
                "titulo" to title,
                "cuerpo" to body.take(200),
                "remitente" to fallbackSender,
                "senderName" to fallbackSender,
                "fechaHora" to Timestamp.now(),
                "leida" to false,
                "relatedId" to relatedId,
                "type" to "event"
            )
            db.collection("users").document(sid).collection("notifications").add(notif)
        } catch (_: Exception) { }
    }
}

// Resolve current user's display name then call createNotifsForIds with senderName set
fun resolveAndCreateNotifs(ids: List<String>, title: String, body: String, relatedId: String, db: FirebaseFirestore, auth: FirebaseAuth) {
    val uid = auth.currentUser?.uid
    if (uid == null) {
        createNotifsForIds(ids, title, body, relatedId, db, auth)
        return
    }
    try {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: doc.getString("displayName") ?: auth.currentUser?.email ?: uid
            // create notifications with senderName field
            for (sid in ids) {
                try {
                    val notif = hashMapOf(
                        "titulo" to title,
                        "cuerpo" to body.take(200),
                        "remitente" to uid,
                        "senderName" to name,
                        "fechaHora" to Timestamp.now(),
                        "leida" to false,
                        "relatedId" to relatedId,
                        "type" to "event"
                    )
                    db.collection("users").document(sid).collection("notifications").add(notif)
                } catch (_: Exception) {}
            }
        }.addOnFailureListener {
            createNotifsForIds(ids, title, body, relatedId, db, auth)
        }
    } catch (_: Exception) {
        createNotifsForIds(ids, title, body, relatedId, db, auth)
    }
}
