package com.example.appcolegios.notificaciones

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Notification
import com.example.appcolegios.util.DateFormats
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class NotificationsUiState(
    val notifications: Map<String, List<Notification>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class NotificationsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh(cutoffDays: Int? = null) {
        loadNotifications(cutoffDays)
    }

    private fun loadNotifications(cutoffDays: Int? = null) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = NotificationsUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val baseQuery = db.collection("users").document(userId)
                    .collection("notifications")
                    .orderBy("fechaHora", Query.Direction.DESCENDING)

                val query = if (cutoffDays != null) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -cutoffDays)
                    val cutoff = com.google.firebase.Timestamp(cal.time)
                    baseQuery.whereGreaterThanOrEqualTo("fechaHora", cutoff)
                } else baseQuery

                // 1) notificaciones personales
                val snapshot = query.get().await()

                val personalNotifications = snapshot.documents.mapNotNull { doc ->
                    val n = doc.toObject(Notification::class.java)
                    n?.copy(id = doc.id)
                }.toMutableList()

                // 2) intentar obtener courseIds si existe doc students/{userId} (útil para estudiantes)
                var courseIds: List<String> = emptyList()
                try {
                    val sdoc = db.collection("students").document(userId).get().await()
                    if (sdoc.exists()) {
                        val raw = sdoc.get("courses") as? List<*>
                        courseIds = raw?.mapNotNull { it?.toString() } ?: emptyList()
                    }
                } catch (_: Exception) {
                    // ignore, no es crítico
                }

                // construir cutoff timestamp para consultas adicionales
                val cutoffTs = if (cutoffDays != null) {
                    val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -cutoffDays); com.google.firebase.Timestamp(cal.time)
                } else null

                val extraNotifications = mutableListOf<Notification>()

                // Si el usuario es PADRE, intentar cargar notificaciones de hijos vinculados
                try {
                    val userDoc = db.collection("users").document(userId).get().await()
                    val roleString = userDoc.getString("role") ?: userDoc.getString("rol") ?: ""
                    // roleString ya es no-nulo (se normaliza a "" si falta). Evaluar igualdad ignorando mayúsculas.
                    if (roleString.equals("PADRE", ignoreCase = true) || roleString.equals("PARENT", ignoreCase = true)) {
                        // reunir posibles childIds buscando en students/users por parents array, acudienteId o acudienteEmail
                        val childIds = mutableSetOf<String>()
                        val userEmail = auth.currentUser?.email
                        try {
                            // students where parents array contains userId
                            val q1 = db.collection("students").whereArrayContains("parents", userId).get().await()
                            for (d in q1.documents) childIds.add(d.id)
                        } catch (_: Exception) { }
                        try {
                            // students where acudienteId == userId
                            val q2 = db.collection("students").whereEqualTo("acudienteId", userId).get().await()
                            for (d in q2.documents) childIds.add(d.id)
                        } catch (_: Exception) { }
                        try {
                            // students where acudienteEmail == userEmail
                            if (!userEmail.isNullOrBlank()) {
                                val q3 = db.collection("students").whereEqualTo("acudienteEmail", userEmail).get().await()
                                for (d in q3.documents) childIds.add(d.id)
                            }
                        } catch (_: Exception) { }
                        try {
                            // users where parents array contains userId
                            val q4 = db.collection("users").whereArrayContains("parents", userId).get().await()
                            for (d in q4.documents) childIds.add(d.id)
                        } catch (_: Exception) { }
                        try {
                            // users where acudienteEmail == userEmail
                            if (!userEmail.isNullOrBlank()) {
                                val q5 = db.collection("users").whereEqualTo("acudienteEmail", userEmail).get().await()
                                for (d in q5.documents) childIds.add(d.id)
                            }
                        } catch (_: Exception) { }

                        // Para cada childId, leer su colección users/{childId}/notifications
                        for (cid in childIds) {
                            try {
                                val csnap = db.collection("users").document(cid).collection("notifications").orderBy("fechaHora", Query.Direction.DESCENDING).get().await()
                                // intentar resolver nombre del hijo (students o users)
                                var childName: String? = null
                                try {
                                    val sdoc = db.collection("students").document(cid).get().await()
                                    if (sdoc.exists()) childName = sdoc.getString("nombre") ?: sdoc.getString("name")
                                } catch (_: Exception) { }
                                if (childName.isNullOrBlank()) {
                                    try {
                                        val udoc = db.collection("users").document(cid).get().await()
                                        if (udoc.exists()) childName = udoc.getString("name") ?: udoc.getString("displayName")
                                    } catch (_: Exception) { }
                                }

                                for (doc in csnap.documents) {
                                    val n = doc.toObject(Notification::class.java)
                                    if (n != null) {
                                        // prefijar id para distinguir notifs de hijos
                                        val prefixedId = "child_${cid}_${doc.id}"
                                        val mapped = n.copy(
                                            id = prefixedId,
                                            remitente = childName ?: n.remitente,
                                            senderName = n.senderName ?: childName
                                        )
                                        extraNotifications.add(mapped)
                                    }
                                }
                            } catch (_: Exception) {
                                // silenciar: puede no tener permisos o no existir
                            }
                        }
                    }
                } catch (_: Exception) {
                    // no crítico
                }

                // 3) leer eventos globales/por curso desde collection 'events' (campo 'date')
                try {
                    // leer una ventana de eventos recientes y filtrar cliente-side por courseId
                    var evQuery: Query = db.collection("events").orderBy("date", Query.Direction.DESCENDING)
                    if (cutoffTs != null) evQuery = evQuery.whereGreaterThanOrEqualTo("date", cutoffTs)
                    // limitar para evitar lecturas excesivas
                    evQuery = evQuery.limit(200)

                    val evSnap = evQuery.get().await()
                    for (doc in evSnap.documents) {
                        val docCourse = doc.getString("courseId")
                        // incluir si es global (sin courseId) o si el courseId está en los cursos del estudiante
                        if (!docCourse.isNullOrBlank() && courseIds.isNotEmpty() && !courseIds.contains(docCourse)) {
                            continue
                        }

                        val id = "event_${doc.id}"
                        val title = doc.getString("title") ?: doc.getString("titulo") ?: "Evento"
                        val body = doc.getString("description") ?: doc.getString("cuerpo") ?: ""
                        val tsField = doc.get("date") ?: doc.get("fechaHora")
                        val date = when (tsField) {
                            is com.google.firebase.Timestamp -> tsField.toDate()
                            is Date -> tsField
                            else -> Date()
                        }
                        val owner = doc.getString("ownerId") ?: doc.getString("creator") ?: doc.getString("author") ?: "Calendario"
                        val notif = Notification(
                            id = id,
                            titulo = title,
                            cuerpo = body,
                            remitente = owner,
                            senderName = doc.getString("senderName") ?: owner,
                            fechaHora = date,
                            leida = false,
                            tipo = "evento",
                            relatedId = doc.id
                        )
                        extraNotifications.add(notif)
                    }
                } catch (_: Exception) {
                    // no interrumpe la carga principal
                }

                // 4) leer anuncios/admin messages desde collection 'announcements'
                try {
                    val annCollections = listOf("announcements", "anuncios", "announcement")
                    var totalAnn = 0
                    for (colName in annCollections) {
                        try {
                            var annQuery: Query = db.collection(colName).orderBy("createdAt", Query.Direction.DESCENDING)
                            if (cutoffTs != null) annQuery = annQuery.whereGreaterThanOrEqualTo("createdAt", cutoffTs)
                            val annSnap = annQuery.get().await()
                            Log.d("NotificationsVM", "found ${annSnap.size()} docs in $colName")
                            for (doc in annSnap.documents) {
                                // permitir anuncios globales o dirigidos a cursos
                                val targetCourseField = doc.get("courseId")
                                var include = true
                                if (targetCourseField != null && courseIds.isNotEmpty()) {
                                    when (targetCourseField) {
                                        is String -> if (!courseIds.contains(targetCourseField)) include = false
                                        is List<*> -> {
                                            val targets = targetCourseField.mapNotNull { it?.toString() }
                                            if (targets.none { courseIds.contains(it) }) include = false
                                        }
                                        else -> {
                                            // campos inesperados, intentar string
                                            val asStr = targetCourseField.toString()
                                            if (!courseIds.contains(asStr)) include = false
                                        }
                                    }
                                }
                                if (!include) continue

                                val id = "ann_${doc.id}"
                                val title = doc.getString("title") ?: doc.getString("titulo") ?: "Anuncio"
                                val body = doc.getString("body") ?: doc.getString("cuerpo") ?: doc.getString("description") ?: ""
                                val tsField = doc.get("createdAt") ?: doc.get("fechaHora") ?: doc.get("date")
                                val date = when (tsField) {
                                    is com.google.firebase.Timestamp -> tsField.toDate()
                                    is Date -> tsField
                                    else -> Date()
                                }
                                val sender = doc.getString("senderName") ?: doc.getString("remitente") ?: doc.getString("author") ?: "Administración"
                                val notif = Notification(
                                    id = id,
                                    titulo = title,
                                    cuerpo = body,
                                    remitente = sender,
                                    senderName = sender,
                                    fechaHora = date,
                                    leida = false,
                                    tipo = "anuncio",
                                    relatedId = doc.id
                                )
                                extraNotifications.add(notif)
                                totalAnn++
                            }
                        } catch (iex: Exception) {
                            // la colección puede no existir o no tener el campo createdAt; intentar siguiente nombre
                            Log.d("NotificationsVM", "error leyendo $colName: ${iex.message}")
                        }
                    }
                    Log.d("NotificationsVM", "loaded $totalAnn announcements total")
                } catch (_: Exception) {
                    // collection announcements puede no existir; no es crítico
                }

                // 5) combinar personal + extra, ordenar por fecha y agrupar
                // evitar duplicados: si ya existe una notificación personal que referencia el mismo relatedId,
                // omitimos el extra correspondiente (evita ver el mismo evento dos veces)
                val personalRelated = personalNotifications.mapNotNull { it.relatedId }.toSet()
                val filteredExtra = extraNotifications.filter { it.relatedId == null || !personalRelated.contains(it.relatedId) }
                val all = (personalNotifications + filteredExtra).sortedByDescending { it.fechaHora }
                val groupedNotifications = groupNotificationsByDate(all)
                _uiState.value = NotificationsUiState(notifications = groupedNotifications, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = NotificationsUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun markAsRead(notificationId: String) {
        // Actualización optimista local
        val current = _uiState.value
        val updatedMap = current.notifications.mapValues { (_, list) ->
            list.map { if (it.id == notificationId) it.copy(leida = true) else it }
        }
        _uiState.value = current.copy(notifications = updatedMap)

        // Persistir en Firestore en background
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                // Buscar en el estado actual la notificación para decidir cómo persistirla
                val flat = current.notifications.values.flatten()
                val notif = flat.find { it.id == notificationId }
                if (notif == null) {
                    // intentar actualizar directamente (por compatibilidad)
                    try {
                        db.collection("users").document(userId).collection("notifications").document(notificationId).update("leida", true).await()
                        return@launch
                    } catch (_: Exception) {
                        return@launch
                    }
                }

                // Si la notificación proviene de events/announcements (ids con prefijo), creamos un registro personal
                if (notificationId.startsWith("event_") || notificationId.startsWith("ann_") || notificationId.startsWith("child_")) {
                    // Caso child_: formato child_{childId}_{childDocId}
                    if (notificationId.startsWith("child_")) {
                        try {
                            val rest = notificationId.removePrefix("child_")
                            val idx = rest.indexOf('_')
                            if (idx > 0) {
                                val childId = rest.substring(0, idx)
                                val childDocId = rest.substring(idx + 1)
                                // intentar leer la notificación original del hijo
                                try {
                                    val childDoc = db.collection("users").document(childId).collection("notifications").document(childDocId).get().await()
                                    if (childDoc.exists()) {
                                        val title = childDoc.getString("titulo") ?: childDoc.getString("title") ?: notif.titulo
                                        val body = childDoc.getString("cuerpo") ?: childDoc.getString("body") ?: childDoc.getString("description") ?: notif.cuerpo
                                        val sender = childDoc.getString("senderName") ?: childDoc.getString("remitente") ?: childDoc.getString("author") ?: notif.remitente
                                        val tsField = childDoc.get("fechaHora") ?: childDoc.get("createdAt") ?: childDoc.get("date")
                                        val date = when (tsField) {
                                            is com.google.firebase.Timestamp -> tsField.toDate()
                                            is Date -> tsField
                                            else -> com.google.firebase.Timestamp.now().toDate()
                                        }
                                        val data = hashMapOf<String, Any?>(
                                            "titulo" to title,
                                            "cuerpo" to body,
                                            "remitente" to sender,
                                            "senderName" to sender,
                                            "fechaHora" to com.google.firebase.Timestamp(date),
                                            "leida" to true,
                                            "relatedId" to childDocId,
                                            "childId" to childId,
                                            "type" to (childDoc.getString("type") ?: childDoc.getString("type") ?: notif.tipo)
                                        )
                                        db.collection("users").document(userId).collection("notifications").add(data).await()
                                    } else {
                                        // fallback: crear a partir del objeto notif en memoria
                                        val data = hashMapOf<String, Any?>(
                                            "titulo" to notif.titulo,
                                            "cuerpo" to notif.cuerpo,
                                            "remitente" to notif.remitente,
                                            "senderName" to (notif.senderName ?: notif.remitente),
                                            "fechaHora" to com.google.firebase.Timestamp(notif.fechaHora),
                                            "leida" to true,
                                            "relatedId" to notif.relatedId,
                                            "childId" to null,
                                            "type" to notif.tipo
                                        )
                                        db.collection("users").document(userId).collection("notifications").add(data).await()
                                    }
                                } catch (_: Exception) {
                                    // si falla la lectura del hijo, crear a partir del objeto en memoria
                                    val data = hashMapOf<String, Any?>(
                                        "titulo" to notif.titulo,
                                        "cuerpo" to notif.cuerpo,
                                        "remitente" to notif.remitente,
                                        "senderName" to (notif.senderName ?: notif.remitente),
                                        "fechaHora" to com.google.firebase.Timestamp(notif.fechaHora),
                                        "leida" to true,
                                        "relatedId" to notif.relatedId,
                                        "childId" to null,
                                        "type" to notif.tipo
                                    )
                                    try { db.collection("users").document(userId).collection("notifications").add(data).await() } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) { }
                        return@launch
                    }

                    if (notificationId.startsWith("event_") || notificationId.startsWith("ann_")) {
                         val data = hashMapOf<String, Any?>(
                             "titulo" to notif.titulo,
                             "cuerpo" to notif.cuerpo,
                             "remitente" to notif.remitente,
                             "senderName" to (notif.senderName ?: notif.remitente),
                             "fechaHora" to com.google.firebase.Timestamp(notif.fechaHora),
                             "leida" to true,
                             "relatedId" to notif.relatedId,
                             "type" to notif.tipo
                         )
                         // Guardar como nueva notificación personal (con id generado)
                         db.collection("users").document(userId).collection("notifications").add(data).await()
                    }
                } else {
                     // notificación personal existente: actualizar campo 'leida'
                     db.collection("users").document(userId)
                         .collection("notifications").document(notificationId)
                         .update("leida", true).await()
                 }
             } catch (_: Exception) {
                 // En caso de error remoto, lo ignoramos por simplicidad
             }
         }
     }

    private fun groupNotificationsByDate(notifications: List<Notification>): Map<String, List<Notification>> {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val buckets = linkedMapOf<String, MutableList<Notification>>()
        notifications.forEach { n ->
            val cal = Calendar.getInstance().apply { time = n.fechaHora }
            val key = when {
                isSameDay(cal, today) -> "Hoy"
                isSameDay(cal, yesterday) -> "Ayer"
                else -> DateFormats.formatDate(n.fechaHora)
            }
            buckets.getOrPut(key) { mutableListOf() }.add(n)
        }
        val todayList = buckets.remove("Hoy")
        val yesterdayList = buckets.remove("Ayer")
        val dated = buckets.entries.sortedByDescending { entry ->
            val parts = entry.key.split('/')
            if (parts.size == 3) {
                val d = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val y = parts[2].toIntOrNull() ?: 0
                y * 10000 + m * 100 + d
            } else 0
        }
        val ordered = linkedMapOf<String, List<Notification>>()
        if (todayList != null) ordered["Hoy"] = todayList
        if (yesterdayList != null) ordered["Ayer"] = yesterdayList
        dated.forEach { ordered[it.key] = it.value }
        return ordered
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
