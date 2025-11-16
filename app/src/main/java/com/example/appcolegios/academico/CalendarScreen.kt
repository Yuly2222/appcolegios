package com.example.appcolegios.academico

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.UserData
import com.example.appcolegios.data.model.Role
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.runtime.key
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

enum class EventSource { USER, GLOBAL }

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: Date,
    val type: EventType,
    val source: EventSource = EventSource.USER,
    val ownerId: String? = null // for USER -> userId; for GLOBAL -> courseId or owner
)

enum class EventType {
    CLASE, EXAMEN, TAREA, EVENTO
}

// helper local para selector de hijos (si se necesitase, reintroducir)
// data class ChildInfo(val id: String, val nombre: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(eventId: String? = null) {
    // Separamos mes mostrado y fecha seleccionada
    var displayedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    // selected day y visibilidad del bottom sheet se mantienen en el ViewModel compartido
    var showAddEventDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Eventos para el mes mostrado (se cargan por rango mediante snapshot listener)
    // Usar ViewModel compartido para que todas las rutas compartan el mismo estado
    // Obtener ViewModel scoped al Activity para que sea la misma instancia desde distintos composables
    // obtener Activity de forma segura (evitar casteo directo de LocalContext)
    val ctx = LocalContext.current
    val activityOwner = remember(ctx) {
        var c: Context? = ctx
        var found: ComponentActivity? = null
        while (c is ContextWrapper) {
            if (c is ComponentActivity) { found = c; break }
            c = c.baseContext
        }
        found
    }
    val calendarVm: CalendarViewModel = if (activityOwner != null) viewModel(activityOwner) else viewModel()
    val profileVm: ProfileViewModel = if (activityOwner != null) viewModel(activityOwner) else viewModel()
    val events by calendarVm.events.collectAsState()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    // user role and preferences
    val userPrefs = remember { UserPreferencesRepository(context) }
    val userData by userPrefs.userData.collectAsState(initial = UserData(null, null, null))
    val role = userData.roleEnum
    // courses for teacher/student/child selection
    val userCourses = remember { mutableStateListOf<Pair<String,String>>() }
    // obtener lista de hijos y selección desde ProfileViewModel (fuente de verdad)
    val childrenForParent by profileVm.children.collectAsState(initial = emptyList())
    val selectedChildIndexValue by profileVm.selectedChildIndex.collectAsState(initial = null)
    // estados para edición/eliminación de eventos
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showEditEventDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<CalendarEvent?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Próximos eventos (derivados del ViewModel para mantener una sola fuente de verdad)
    var upcomingLimit by remember { mutableStateOf(25) }
    var selectedFilterType by remember { mutableStateOf<EventType?>(null) }

    // Listener registration manejado con DisposableEffect: soporta múltiples listeners según rol
    // Añadir selectedChildIndexValue a las dependencias para recargar listeners cuando el padre cambie de hijo
    DisposableEffect(displayedMonth.timeInMillis, auth.currentUser?.uid, role, selectedChildIndexValue) {
        val userId = auth.currentUser?.uid
        val regs = mutableListOf<ListenerRegistration>()
        // limpiar eventos en el ViewModel
        calendarVm.setEvents(emptyList())

        if (userId != null) {
            // calcular primer y ultimo día del mes mostrado
            val start = (displayedMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (displayedMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val startTs = Timestamp(start.time)
            val endTs = Timestamp(end.time)

            try {
                // 1) listener to personal events of the viewed user (teacher sees own personal too)
                val personalListener = db.collection("users").document(userId).collection("events")
                    .whereGreaterThanOrEqualTo("date", startTs)
                    .whereLessThanOrEqualTo("date", endTs)
                    .addSnapshotListener { snap, e ->
                        if (e != null) { Log.e("CalendarScreen", "Personal listener error: ${e.message}"); return@addSnapshotListener }
                        if (snap != null) {
                            for (doc in snap.documents) {
                                val id = doc.id
                                val title = doc.getString("title") ?: "Evento"
                                val description = doc.getString("description") ?: ""
                                val ts = doc.get("date")
                                val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.USER, userId))
                                val rec = doc.getString("recurrence")
                                if (!rec.isNullOrBlank() && d != null) {
                                    try { val occ = generateOccurrences(d, rec, start.time, end.time); occ.forEachIndexed { idx, occDate -> if (/*prevent duplicates handled by VM*/ true) calendarVm.addOrUpdateEvent(CalendarEvent("${id}_occ_$idx", title, description, occDate, type, EventSource.USER, userId)) } } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                regs.add(personalListener)

                // 2) role-specific: teacher -> events for their courses; student -> events for courses enrolled; parent -> events for selected child
                when (role) {
                    Role.DOCENTE -> {
                        // get course ids taught by teacher
                        val courseIds = mutableListOf<String>()
                        try {
                            val q = db.collection("courses").whereEqualTo("teacherId", userId).get()
                            q.addOnSuccessListener { snap ->
                                userCourses.clear()
                                for (doc in snap.documents) {
                                    val cid = doc.id
                                    courseIds.add(cid)
                                    val cname = doc.getString("name") ?: doc.getString("title") ?: cid
                                    userCourses.add(Pair(cid, cname))
                                }
                                // query top-level events where courseId in courseIds, chunked by 10
                                if (courseIds.isNotEmpty()) {
                                    courseIds.chunked(10).forEach { chunk ->
                                        val evQuery = db.collection("events").whereIn("courseId", chunk).whereGreaterThanOrEqualTo("date", startTs).whereLessThanOrEqualTo("date", endTs)
                                        val r = evQuery.addSnapshotListener { snap2, e2 ->
                                            if (e2 != null) { Log.e("CalendarScreen","Course events listener err: ${e2.message}"); return@addSnapshotListener }
                                            if (snap2 != null) {
                                                for (doc in snap2.documents) {
                                                    val id = doc.id
                                                    val title = doc.getString("title") ?: "Evento"
                                                    val description = doc.getString("description") ?: ""
                                                    val ts = doc.get("date")
                                                    val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                                    val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                                    if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, null))
                                                }
                                            }
                                        }
                                        regs.add(r)
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    Role.ESTUDIANTE -> {
                        // student: read student doc for courses
                        try {
                            val sdocTask = db.collection("students").document(userId).get()
                            sdocTask.addOnSuccessListener { sdoc ->
                                val courseIds = (sdoc.get("courses") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                // populate userCourses with names
                                userCourses.clear()
                                for (cid in courseIds) {
                                    db.collection("courses").document(cid).get().addOnSuccessListener { cdoc ->
                                        if (cdoc.exists()) userCourses.add(Pair(cid, cdoc.getString("name") ?: cid))
                                    }
                                }
                                if (courseIds.isNotEmpty()) {
                                    courseIds.chunked(10).forEach { chunk ->
                                        val evQuery = db.collection("events").whereIn("courseId", chunk).whereGreaterThanOrEqualTo("date", startTs).whereLessThanOrEqualTo("date", endTs)
                                        val r = evQuery.addSnapshotListener { snap2, e2 ->
                                            if (e2 != null) return@addSnapshotListener
                                            if (snap2 != null) {
                                                for (doc in snap2.documents) {
                                                    val id = doc.id
                                                    val title = doc.getString("title") ?: "Evento"
                                                    val description = doc.getString("description") ?: ""
                                                    val ts = doc.get("date")
                                                    val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                                    val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                                    if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, null))
                                                }
                                            }
                                        }
                                        regs.add(r)
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    Role.PADRE -> {
                        // parent: use selected child from ProfileViewModel
                        val childId = childrenForParent.getOrNull(selectedChildIndexValue ?: 0)?.id
                        if (!childId.isNullOrBlank()) {
                            // personal events of child
                            val childListener = db.collection("users").document(childId).collection("events")
                                .whereGreaterThanOrEqualTo("date", startTs)
                                .whereLessThanOrEqualTo("date", endTs)
                                .addSnapshotListener { snap, e ->
                                    if (e != null) { Log.e("CalendarScreen", "Child listener error: ${e.message}"); return@addSnapshotListener }
                                    if (snap != null) {
                                        for (doc in snap.documents) {
                                            val id = doc.id
                                            val title = doc.getString("title") ?: "Evento"
                                            val description = doc.getString("description") ?: ""
                                            val ts = doc.get("date")
                                            val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                            val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                            if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.USER, childId))
                                        }
                                    }
                                }
                            regs.add(childListener)

                            // also load course events for that child (like student)
                            try {
                                val sdocTask = db.collection("students").document(childId).get()
                                sdocTask.addOnSuccessListener { sdoc ->
                                    val courseIds = (sdoc.get("courses") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                    if (courseIds.isNotEmpty()) {
                                        courseIds.chunked(10).forEach { chunk ->
                                            val evQuery = db.collection("events").whereIn("courseId", chunk).whereGreaterThanOrEqualTo("date", startTs).whereLessThanOrEqualTo("date", endTs)
                                            val r = evQuery.addSnapshotListener { snap2, e2 ->
                                                if (e2 != null) { Log.e("CalendarScreen", "Child course events listener err: ${e2.message}"); return@addSnapshotListener }
                                                if (snap2 != null) {
                                                    for (doc in snap2.documents) {
                                                        val id = doc.id
                                                        val title = doc.getString("title") ?: "Evento"
                                                        val description = doc.getString("description") ?: ""
                                                        val ts = doc.get("date")
                                                        val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                                        val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                                        if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, doc.getString("courseId")))
                                                    }
                                                }
                                            }
                                            regs.add(r)
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    else -> {
                        // default: no extra listeners
                    }
                } // <-- SE AGREGA ESTA LLAVE PARA CERRAR EL when(role)

                // Nuevo: listener global para eventos creados por ADMIN (visibles desde el menú calendario)
                try {
                    val adminQuery = db.collection("events")
                        .whereEqualTo("senderName", "ADMIN")
                        .whereGreaterThanOrEqualTo("date", startTs)
                        .whereLessThanOrEqualTo("date", endTs)

                    val adminReg = adminQuery.addSnapshotListener { snapA, eA ->
                        if (eA != null) { Log.e("CalendarScreen", "Admin events listener err: ${eA.message}"); return@addSnapshotListener }
                        if (snapA != null) {
                            for (doc in snapA.documents) {
                                val id = doc.id
                                val title = doc.getString("title") ?: "Evento"
                                val description = doc.getString("description") ?: ""
                                val ts = doc.get("date")
                                val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                if (d != null) {
                                    // marcar como GLOBAL (evento institucional)
                                    calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, doc.getString("courseId")))
                                }
                            }
                        }
                    }
                    regs.add(adminReg)
                } catch (_: Exception) { }

            } catch (ex: Exception) {
                Log.e("CalendarScreen", "Error starting listeners: ${ex.message}", ex)
            }
        }

        onDispose {
            regs.forEach { try { it.remove() } catch (_: Exception) {} }
        }
    }

    // Snackbar / scaffold
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
                    displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedMonth.time),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { // volver a hoy
                        displayedMonth = Calendar.getInstance()
                        calendarVm.setSelectedDay(Calendar.getInstance().time)
                    }) { Text("Hoy") }

                    // selector de hijo para PADRE (usar ProfileViewModel como fuente)
                    if (role == Role.PADRE) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hijo: ")
                            if (childrenForParent.isEmpty()) {
                                Text("(sin hijos)")
                            } else {
                                var expanded by remember { mutableStateOf(false) }
                                val selIdx = selectedChildIndexValue ?: 0
                                val label = childrenForParent.getOrNull(selIdx)?.nombre ?: "Seleccionar"
                                Box {
                                    TextButton(onClick = { expanded = true }) { Text(label) }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        childrenForParent.forEachIndexed { idx, child ->
                                            DropdownMenuItem(text = { Text(child.nombre) }, onClick = { profileVm.selectChildAtIndex(idx); expanded = false })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                IconButton(onClick = {
                    displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Días de la semana -- respetar locale (primer día simplificado)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Obtener nombres de días desde locale
                val df = SimpleDateFormat("EE", Locale.getDefault())
                val headerCal = Calendar.getInstance()
                // usar firstDayOfWeek del locale
                val firstDow = headerCal.firstDayOfWeek
                for (i in 0 until 7) {
                    headerCal.set(Calendar.DAY_OF_WEEK, (firstDow + i - 1) % 7 + 1)
                    Text(
                        text = df.format(headerCal.time),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Grid del calendario con fecha seleccionada y soporte swipe/anim
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        val threshold = 150
                        if (dragAmount > threshold) {
                            // swipe derecha -> mes anterior
                            displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        } else if (dragAmount < -threshold) {
                            // swipe izquierda -> mes siguiente
                            displayedMonth = (displayedMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        }
                    }
                }
            ) {
                AnimatedContent(targetState = displayedMonth.timeInMillis, transitionSpec = {
                    slideInHorizontally(initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth }, animationSpec = tween(300))
                        .togetherWith(
                            slideOutHorizontally(targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth }, animationSpec = tween(300))
                        )
                }) { ts ->
                       key(ts) {
                           // pasar la fecha seleccionada desde el ViewModel
                           val selectedMillisLocal by calendarVm.selectedDayMillis.collectAsState()
                           val selectedCalForGrid = selectedMillisLocal?.let { Calendar.getInstance().apply { timeInMillis = it } }
                           CalendarGrid(
                               displayedMonth = displayedMonth,
                               selectedDay = selectedCalForGrid,
                               events = events,
                               onDateSelected = { cal ->
                                   // delegar la selección al ViewModel compartido
                                   calendarVm.setSelectedDay(cal.time)
                               }
                           )
                       }
                   }
             }

            Spacer(Modifier.height(16.dp))

            // Botón para agregar evento (solo para DOCENTE y ADMIN)
            if (role == Role.DOCENTE || role == Role.ADMIN) {
                Button(
                    onClick = { showAddEventDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar evento")
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar Evento")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Lista de próximos eventos (paginated) con filtro por tipo
            Text(
                "Próximos Eventos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            // Filtro por tipo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtrar: ")
                Spacer(Modifier.width(8.dp))
                // botones simples para filtrar por tipo
                EventType.entries.let { types ->
                    types.forEach { t ->
                         val selected = selectedFilterType == t
                         TextButton(onClick = { selectedFilterType = if (selected) null else t }) {
                             Text(t.name, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                         }
                     }
                 }
            }

            Spacer(Modifier.height(8.dp))

            // Mostrar próximos eventos a partir del ViewModel (mismo origen que el grid)
            val nowDate = remember { Date() }
            // derive upcoming list from calendarVm.events (single source of truth)
            val allUpcoming = remember(events, nowDate) {
                events.filter { it.date.after(nowDate) || it.date == nowDate }.sortedBy { it.date }
            }
            val filteredUpcoming = if (selectedFilterType == null) allUpcoming else allUpcoming.filter { it.type == selectedFilterType }
            val displayedUpcoming = filteredUpcoming.take(upcomingLimit)

            LazyColumn(
                 verticalArrangement = Arrangement.spacedBy(8.dp)
             ) {
                items(displayedUpcoming) { event ->
                    // per-event permissions: owner can edit/delete USER events; DOCENTE can edit/delete GLOBAL events
                    val uid = auth.currentUser?.uid
                    val allowEdit = when (event.source) {
                        EventSource.USER -> event.ownerId == uid
                        EventSource.GLOBAL -> role == Role.DOCENTE
                    }
                    val allowDelete = allowEdit
                    EventCard(event,
                        onEdit = if (allowEdit) { ev -> editingEvent = ev; showEditEventDialog = true } else null,
                        onDelete = if (allowDelete) { ev -> eventToDelete = ev; showDeleteConfirm = true } else null
                    )
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        val totalAvailable = filteredUpcoming.size
                        if (displayedUpcoming.size < totalAvailable) {
                            Button(onClick = { upcomingLimit += 25 }) { Text("Cargar más") }
                        }
                     }
                  }
              }
          }
     }

    // Bottom sheet para eventos del día seleccionado (simple Modal)
    // Usar el estado del ViewModel para selección y bottom sheet
    val selectedMillis by calendarVm.selectedDayMillis.collectAsState()
    val bottomVisibleState by calendarVm.bottomSheetVisible.collectAsState()

    if (bottomVisibleState && selectedMillis != null) {
        val selLocal = Instant.ofEpochMilli(selectedMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
        val dayEvents = events.filter { ev ->
            val evLocal = Instant.ofEpochMilli(ev.date.time).atZone(ZoneId.systemDefault()).toLocalDate()
            evLocal == selLocal
        }.sortedBy { it.date }

        ModalBottomSheet(
            onDismissRequest = { calendarVm.setBottomSheetVisible(false) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Eventos en ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedMillis!!))}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (dayEvents.isEmpty()) {
                    Text("No hay eventos para este día", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dayEvents.forEach { ev ->
                        val uid = auth.currentUser?.uid
                        val allowEdit = when (ev.source) {
                            EventSource.USER -> ev.ownerId == uid
                            EventSource.GLOBAL -> role == Role.DOCENTE
                        }
                        val allowDelete = allowEdit
                        EventCard(ev,
                            onEdit = if (allowEdit) { e -> editingEvent = e; showEditEventDialog = true } else null,
                            onDelete = if (allowDelete) { e -> eventToDelete = e; showDeleteConfirm = true } else null
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { calendarVm.setBottomSheetVisible(false) }) { Text("Cerrar") }
                }
            }
        }
    }

    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedMillis?.let { Date(it) } ?: displayedMonth.time,
            onDismiss = { showAddEventDialog = false },
            onSave = { title, description, date, type, targetCourseId ->
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    scope.launch { snackbarHostState.showSnackbar("Debes iniciar sesión para agregar eventos") }
                    return@AddEventDialog
                }
                val localDb = FirebaseFirestore.getInstance()
                val localEventId = UUID.randomUUID().toString()
                val eventData = hashMapOf<String, Any?>(
                    "id" to localEventId,
                    "title" to title,
                    "description" to description,
                    "date" to Timestamp(date),
                    "type" to type.name,
                    "createdAt" to Timestamp.now(),
                    "ownerId" to userId
                )
                // If teacher selected a course target, save as global event in top-level collection
                if (role == Role.DOCENTE && !targetCourseId.isNullOrBlank()) {
                    eventData["courseId"] = targetCourseId
                    localDb.collection("events").document(localEventId)
                        .set(eventData)
                        .addOnSuccessListener {
                            calendarVm.addOrUpdateEvent(CalendarEvent(localEventId, title, description, date, type, EventSource.GLOBAL, targetCourseId))
                            showAddEventDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Evento de curso agregado") }

                            // Crear notificaciones para estudiantes del curso (solo si el creador no es estudiante)
                            try {
                                // obtener estudiantes desde subcolección courses/{courseId}/students
                                val tcid = targetCourseId
                                localDb.collection("courses").document(tcid).collection("students").get()
                                    .addOnSuccessListener { studsSnap ->
                                        val studentIds = if (!studsSnap.isEmpty) studsSnap.documents.mapNotNull { it.id } else emptyList()
                                        if (studentIds.isEmpty()) {
                                            // fallback: si no hay subcoleccion, intentar buscar en students collection por courseId
                                            localDb.collection("students").whereEqualTo("courseId", tcid).get()
                                                .addOnSuccessListener { altSnap ->
                                                    val altIds = altSnap.documents.mapNotNull { it.id }
                                                    // resolver senderName y crear notifs
                                                    resolveAndCreateNotifs(altIds, title, description, localEventId, localDb, auth)
                                                }
                                        } else {
                                            resolveAndCreateNotifs(studentIds, title, description, localEventId, localDb, auth)
                                        }
                                    }
                            } catch (e: Exception) {
                                Log.w("CalendarScreen", "Error creando notificaciones de curso: ${e.message}")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("CalendarScreen", "Error saving global event: ${e.message}", e)
                            scope.launch { snackbarHostState.showSnackbar("Error guardando evento") }
                        }
                } else {
                    // save personal event under users/{uid}/events
                    localDb.collection("users").document(userId).collection("events").document(localEventId)
                        .set(eventData)
                        .addOnSuccessListener {
                            calendarVm.addOrUpdateEvent(CalendarEvent(localEventId, title, description, date, type, EventSource.USER, userId))
                            showAddEventDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Evento agregado") }
                        }
                        .addOnFailureListener { e ->
                            Log.e("CalendarScreen", "Error saving personal event: ${e.message}", e)
                            scope.launch { snackbarHostState.showSnackbar("Error guardando evento") }
                        }
                }
            },
            role = role,
            userCourses = userCourses.toList()
        )
    }

    // handle actual deletion when confirmed
    if (showDeleteConfirm && eventToDelete != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false; eventToDelete = null }, title = { Text("Confirmar eliminación") }, text = { Text("Eliminar evento '${eventToDelete!!.title}'?") }, confirmButton = {
            TextButton(onClick = {
                val uid = auth.currentUser?.uid ?: run { showDeleteConfirm = false; eventToDelete = null; return@TextButton }
                val ev = eventToDelete
                if (ev == null) return@TextButton
                if (ev.source == EventSource.USER) {
                    // delete under owner user's events
                    val owner = ev.ownerId ?: uid
                    db.collection("users").document(owner).collection("events").document(ev.id)
                        .delete()
                        .addOnSuccessListener {
                            calendarVm.removeEvent(ev.id)
                            // no local upcomingEvents state any more: view is derived from calendarVm.events
                            showDeleteConfirm = false
                            eventToDelete = null
                        }
                        .addOnFailureListener { ex ->
                            scope.launch { snackbarHostState.showSnackbar("Error eliminando: ${ex.localizedMessage}") }
                            showDeleteConfirm = false; eventToDelete = null
                        }
                } else {
                    // global event stored in top-level 'events'
                    db.collection("events").document(ev.id)
                        .delete()
                        .addOnSuccessListener {
                            calendarVm.removeEvent(ev.id)
                            // no local upcomingEvents state any more
                            showDeleteConfirm = false
                            eventToDelete = null
                        }
                        .addOnFailureListener { ex ->
                            scope.launch { snackbarHostState.showSnackbar("Error eliminando: ${ex.localizedMessage}") }
                            showDeleteConfirm = false; eventToDelete = null
                        }
                }
            }) { Text("Eliminar") }
        }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false; eventToDelete = null }) { Text("Cancelar") } })
    }

    // Edit dialog
    if (showEditEventDialog && editingEvent != null) {
        EditEventDialog(event = editingEvent!!, onDismiss = { showEditEventDialog = false; editingEvent = null }, onSave = { updated ->
             val uid = auth.currentUser?.uid ?: return@EditEventDialog
             val map = mapOf("title" to updated.title, "description" to updated.description, "date" to Timestamp(updated.date), "type" to updated.type.name)
             // update in appropriate collection based on source
             if (updated.source == EventSource.USER) {
                // actualizar en la colección del owner (no asumir uid actual)
                val owner = updated.ownerId ?: uid
                db.collection("users").document(owner).collection("events").document(updated.id)
                    .update(map)
                    .addOnSuccessListener {
                        // actualizar también en ViewModel compartido
                        calendarVm.addOrUpdateEvent(updated)
                         showEditEventDialog = false; editingEvent = null
                    }
                    .addOnFailureListener { ex -> scope.launch { snackbarHostState.showSnackbar("Error actualizando: ${ex.localizedMessage}") } }
             } else {
                 // teacher editing global event
                 val mapWithCourse = hashMapOf<String, Any?>()
                 mapWithCourse.putAll(map)
                 if (!updated.ownerId.isNullOrBlank()) mapWithCourse["courseId"] = updated.ownerId
                 db.collection("events").document(updated.id).update(mapWithCourse as Map<String, Any?>)
                      .addOnSuccessListener {
                          // actualizar también en ViewModel compartido
                          calendarVm.addOrUpdateEvent(updated)
                           showEditEventDialog = false
                           editingEvent = null
                       }
                       .addOnFailureListener { ex -> scope.launch { snackbarHostState.showSnackbar("Error actualizando: ${ex.localizedMessage}") } }
             }
         }, role = role, userCourses = userCourses.toList())
       }

    // After listeners are set up, if an eventId was provided attempt to locate and select it
    LaunchedEffect(eventId) {
        if (eventId.isNullOrBlank()) return@LaunchedEffect
        // selectEventById accepts a single eventId string in the ViewModel
        calendarVm.selectEventById(eventId)
    }
}

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
private fun CalendarGrid(
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

@Composable
private fun EventCard(event: CalendarEvent, onEdit: ((CalendarEvent) -> Unit)? = null, onDelete: ((CalendarEvent) -> Unit)? = null) {
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
                        EventType.TAREA -> Icons.AutoMirrored.Filled.Assignment
                        EventType.EVENTO -> Icons.AutoMirrored.Filled.EventNote
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
private fun AddEventDialog(
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
         android.app.DatePickerDialog(context, { _, y, m, d ->
             c.set(Calendar.YEAR, y)
             c.set(Calendar.MONTH, m)
             c.set(Calendar.DAY_OF_MONTH, d)
             dateCal = c
         }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
     }

     fun openTimePicker() {
         val c = dateCal
         android.app.TimePickerDialog(context, { _, hour, minute ->
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

                 Spacer(Modifier.height(8.dp))

                 Column(modifier = Modifier.fillMaxWidth()) {
                     EventType.values().forEach { ev ->
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 4.dp)
                         ) {
                             RadioButton(selected = type == ev, onClick = { type = ev })
                             Spacer(Modifier.width(8.dp))
                             Text(ev.name, modifier = Modifier.weight(1f))
                         }
                     }
                 }

                 // Selección de curso solo para docentes
                 if (role == Role.DOCENTE) {
                     Spacer(Modifier.height(8.dp))
                     Text("Curso objetivo (opcional):", style = MaterialTheme.typography.labelMedium)
                     LazyColumn(
                         modifier = Modifier.heightIn(max = 200.dp),
                         verticalArrangement = Arrangement.spacedBy(4.dp)
                     ) {
                         items(userCourses) { course ->
                             val isSelected = targetCourseId == course.first
                             Row(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clickable { targetCourseId = if (isSelected) null else course.first }
                                     .padding(8.dp)
                                     .background(
                                         color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                         shape = CircleShape
                                     ),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Text(course.second, modifier = Modifier.weight(1f))
                                 if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
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
private fun EditEventDialog(event: CalendarEvent, onDismiss: () -> Unit, onSave: (CalendarEvent) -> Unit, role: Role?, userCourses: List<Pair<String,String>> = emptyList()) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(event.title) }
    var description by remember { mutableStateOf(event.description) }
    var dateCal by remember { mutableStateOf(Calendar.getInstance().apply { time = event.date }) }
    var type by remember { mutableStateOf(event.type) }
    var targetCourseId by remember { mutableStateOf<String?>(null) }

    // cargar id de curso objetivo si es evento global
    LaunchedEffect(event) {
        targetCourseId = if (event.source == EventSource.GLOBAL) event.ownerId else null
    }

    fun openDatePicker() {
        val c = dateCal
        android.app.DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d); dateCal = c }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun openTimePicker() {
        val c = dateCal
        android.app.TimePickerDialog(context, { _, h, min -> c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min); dateCal = c }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
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
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                EventType.entries.forEach { ev ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = type == ev, onClick = { type = ev })
                        Spacer(Modifier.width(8.dp))
                        Text(ev.name, modifier = Modifier.weight(1f))
                    }
                }
            }

            // target course selection for teachers
            if (role == Role.DOCENTE) {
                Spacer(Modifier.height(8.dp))
                Text("Curso objetivo (opcional):", style = MaterialTheme.typography.labelMedium)
                // mostrar cursos disponibles para el docente
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(userCourses) { course ->
                        val isSelected = targetCourseId == course.first
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { targetCourseId = if (isSelected) null else course.first }
                                .padding(8.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = CircleShape
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(course.second, modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(CalendarEvent(event.id, title, description, dateCal.time, type, event.source, targetCourseId)) }) { Text("Guardar") }
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
