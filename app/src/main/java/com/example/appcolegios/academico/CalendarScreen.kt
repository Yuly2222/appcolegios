package com.example.appcolegios.academico

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// NOTA: helpers del mismo paquete no requieren import explícito; evitamos imports redundantes que generan warnings

enum class EventSource { USER, GLOBAL }

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: Date,
    val type: EventType,
    val source: EventSource = EventSource.USER,
    val ownerId: String? = null, // for USER -> userId (owner)
    val creatorId: String? = null, // quien creó el evento (userId)
    val courseId: String? = null // for GLOBAL -> courseId
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
                                // En listeners, cuando se agrega un evento desde users/{uid}/events (personal)
                                if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.USER, userId, /*creatorId=*/doc.getString("ownerId") ?: userId, /*courseId=*/null))
                                val rec = doc.getString("recurrence")
                                if (!rec.isNullOrBlank() && d != null) {
                                    try {
                                        val occ = generateOccurrences(d, rec, start.time, end.time)
                                        occ.forEachIndexed { idx: Int, occDate: Date ->
                                            // prevenir duplicados lo maneja el ViewModel
                                            calendarVm.addOrUpdateEvent(CalendarEvent("${id}_occ_$idx", title, description, occDate, type, EventSource.USER, userId, doc.getString("ownerId") ?: userId, null))
                                        }
                                    } catch (_: Exception) {}
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
                                                    if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, /*ownerId=*/null, /*creatorId*/doc.getString("ownerId"), /*courseId=*/doc.getString("courseId")))
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
                                                    if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, /*ownerId=*/null, /*creatorId*/doc.getString("ownerId"), /*courseId=*/doc.getString("courseId")))
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
                                            if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.USER, childId, /*creatorId=*/doc.getString("ownerId") ?: childId, null))
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
                                                        if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, /*ownerId=*/null, /*creatorId*/doc.getString("ownerId"), /*courseId=*/doc.getString("courseId")))
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
                } // cierre correcto del when(role)

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
                                    calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, null, doc.getString("ownerId"), doc.getString("courseId")))
                                }
                            }
                        }
                    }
                    regs.add(adminReg)
                } catch (_: Exception) { }

                // Si es ADMIN, también suscribirse a todos los eventos personales (collectionGroup) y a todos los eventos globales
                if (role == Role.ADMIN) {
                    try {
                        val allUsersReg = db.collectionGroup("events")
                            .whereGreaterThanOrEqualTo("date", startTs)
                            .whereLessThanOrEqualTo("date", endTs)
                            .addSnapshotListener { snapU, eU ->
                                if (eU != null) { Log.e("CalendarScreen", "All users events listener err: ${eU.message}"); return@addSnapshotListener }
                                if (snapU != null) {
                                    for (doc in snapU.documents) {
                                        val id = doc.id
                                        val title = doc.getString("title") ?: "Evento"
                                        val description = doc.getString("description") ?: ""
                                        val ts = doc.get("date")
                                        val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                        val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                        // intentar obtener ownerId desde el doc o desde la ruta users/{uid}/events/{id}
                                        val ownerFromField = doc.getString("ownerId")
                                        val ownerFromPath = try { doc.reference.parent.parent?.id } catch (_: Exception) { null }
                                        val owner = ownerFromField ?: ownerFromPath
                                        if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.USER, owner))
                                    }
                                }
                            }
                        regs.add(allUsersReg)
                    } catch (_: Exception) { }

                    try {
                        val allGlobal = db.collection("events")
                            .whereGreaterThanOrEqualTo("date", startTs)
                            .whereLessThanOrEqualTo("date", endTs)
                        val allGlobalReg = allGlobal.addSnapshotListener { snapG, eG ->
                            if (eG != null) { Log.e("CalendarScreen", "All global events listener err: ${eG.message}"); return@addSnapshotListener }
                            if (snapG != null) {
                                for (doc in snapG.documents) {
                                    val id = doc.id
                                    val title = doc.getString("title") ?: "Evento"
                                    val description = doc.getString("description") ?: ""
                                    val ts = doc.get("date")
                                    val d = when (ts) { is Timestamp -> ts.toDate(); is Date -> ts; else -> null }
                                    val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                                    if (d != null) calendarVm.addOrUpdateEvent(CalendarEvent(id, title, description, d, type, EventSource.GLOBAL, null, /*creatorId=*/doc.getString("ownerId"), /*courseId=*/doc.getString("courseId")))
                                }
                            }
                        }
                        regs.add(allGlobalReg)
                    } catch (_: Exception) { }
                }

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
                           val gridEvents = if (selectedFilterType == null) events else events.filter { it.type == selectedFilterType }
                           CalendarGrid(
                               displayedMonth = displayedMonth,
                               selectedDay = selectedCalForGrid,
                               events = gridEvents,
                               onDateSelected = { cal: Calendar ->
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
                ElevatedButton(
                    onClick = { showAddEventDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar evento")
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar Evento", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

            // Filtro: chips + botón en LazyRow para que mantengan su tamaño natural y no se compriman
            Text("Filtrar:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            // Usar LazyRow para que cada chip y el botón midan su ancho natural y el usuario pueda desplazar horizontalmente
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                val typesRow = listOf(EventType.CLASE, EventType.EXAMEN, EventType.TAREA)
                items(typesRow) { t ->
                    val selected = selectedFilterType == t
                    FilterChip(
                        selected = selected,
                        onClick = { selectedFilterType = if (selected) null else t },
                        label = { Text(t.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (t) {
                                    EventType.CLASE -> Icons.Filled.School
                                    EventType.EXAMEN -> Icons.Filled.Warning
                                    EventType.TAREA -> Icons.AutoMirrored.Filled.Assignment
                                    else -> Icons.Filled.Info
                                },
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    )
                }

                item {
                    // Separador pequeño antes del botón
                    Spacer(Modifier.width(3.dp))
                }

                item {
                    // Botón 'Eventos' con tamaño natural (no fillMaxWidth) para que sea legible
                    ElevatedButton(
                        onClick = {
                            selectedFilterType = if (selectedFilterType == EventType.EVENTO) null else EventType.EVENTO
                        },
                        modifier = Modifier.height(48.dp).defaultMinSize(minWidth = 120.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Event, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Eventos", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

            // Mantener el área de filtros visible: poner la lista de próximos eventos en un contenedor con altura fija
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = displayedUpcoming.size) { idx: Int ->
                        val event = displayedUpcoming[idx]
                     // per-event permissions: owner can edit/delete USER events; DOCENTE can edit/delete GLOBAL events only if they are the creator
                     val uid = auth.currentUser?.uid
                     val allowEdit = when (event.source) {
                         EventSource.USER -> (event.ownerId == uid) || (role == Role.ADMIN)
                         EventSource.GLOBAL -> (role == Role.ADMIN) || (role == Role.DOCENTE && event.creatorId == uid)
                     }
                     val allowDelete = allowEdit
                     EventCard(event,
                         onEdit = if (allowEdit) { ev: CalendarEvent -> editingEvent = ev; showEditEventDialog = true } else null,
                         onDelete = if (allowDelete) { ev: CalendarEvent -> eventToDelete = ev; showDeleteConfirm = true } else null
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
                            EventSource.USER -> (ev.ownerId == uid) || (role == Role.ADMIN)
                            EventSource.GLOBAL -> (role == Role.ADMIN) || (role == Role.DOCENTE && ev.creatorId == uid)
                        }
                        val allowDelete = allowEdit
                        EventCard(ev,
                            onEdit = if (allowEdit) { e: CalendarEvent -> editingEvent = e; showEditEventDialog = true } else null,
                            onDelete = if (allowDelete) { e: CalendarEvent -> eventToDelete = e; showDeleteConfirm = true } else null
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
            onSave = { title: String, description: String, date: Date, type: EventType, targetCourseId: String? ->
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
                    eventData["ownerId"] = userId // ownerId used as creator in existing db
                    localDb.collection("events").document(localEventId)
                        .set(eventData)
                        .addOnSuccessListener {
                            calendarVm.addOrUpdateEvent(CalendarEvent(localEventId, title, description, date, type, EventSource.GLOBAL, /*ownerId*/null, /*creatorId*/userId, /*courseId*/targetCourseId))
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
                    eventData["ownerId"] = userId
                    localDb.collection("users").document(userId).collection("events").document(localEventId)
                        .set(eventData)
                        .addOnSuccessListener {
                            calendarVm.addOrUpdateEvent(CalendarEvent(localEventId, title, description, date, type, EventSource.USER, userId, /*creatorId*/userId, null))
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
                 // teacher or admin editing global event
                 val mapWithCourse = hashMapOf<String, Any?>()
                 mapWithCourse.putAll(map)
                 if (!updated.courseId.isNullOrBlank()) mapWithCourse["courseId"] = updated.courseId
                 if (!updated.creatorId.isNullOrBlank()) mapWithCourse["ownerId"] = updated.creatorId
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


    }
// helper functions (moved to CalendarScreenHelpers.kt)
