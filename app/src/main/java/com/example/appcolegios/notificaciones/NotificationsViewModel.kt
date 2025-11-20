package com.example.appcolegios.notificaciones

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.FirestoreRepository
import com.example.appcolegios.data.model.Notification
import com.example.appcolegios.util.DateFormats
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

data class NotificationsUiState(
    val notifications: Map<String, List<Notification>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class NotificationsViewModel : ViewModel() {
    private val repo = FirestoreRepository()
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
                // 1) notificaciones personales: leer users/{userId}/notifications usando helper
                val personalDocs = repo.getSubcollectionDocuments("users", userId, "notifications")
                val personalNotifications = personalDocs.mapNotNull { doc ->
                    try {
                        val n = Notification(
                            id = doc["__id"] as? String ?: "",
                            titulo = doc["titulo"] as? String ?: doc["title"] as? String ?: "",
                            cuerpo = doc["cuerpo"] as? String ?: doc["body"] as? String ?: doc["description"] as? String ?: "",
                            remitente = doc["remitente"] as? String ?: doc["senderName"] as? String ?: "",
                            senderName = doc["senderName"] as? String ?: doc["remitente"] as? String,
                            fechaHora = when (val t = doc["fechaHora"]) {
                                is com.google.firebase.Timestamp -> t.toDate()
                                is Date -> t
                                else -> Date()
                            },
                            leida = (doc["leida"] as? Boolean) ?: false,
                            tipo = doc["type"] as? String ?: doc["tipo"] as? String ?: "",
                            relatedId = doc["relatedId"] as? String
                        )
                        n
                    } catch (_: Exception) { null }
                }.toMutableList()

                // 2) intentar obtener courseIds si existe doc students/{userId} (útil para estudiantes)
                var courseIds: List<String> = emptyList()
                try {
                    val sdoc = repo.getDocumentData("students", userId)
                    if (sdoc != null) {
                        val raw = sdoc["courses"] as? List<*>
                        courseIds = raw?.mapNotNull { it?.toString() } ?: emptyList()
                    }
                } catch (_: Exception) { }

                // construir cutoff timestamp para consultas adicionales
                val cutoffTs = if (cutoffDays != null) {
                    val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -cutoffDays); com.google.firebase.Timestamp(cal.time)
                } else null

                val extraNotifications = mutableListOf<Notification>()

                // Si el usuario es PADRE, intentar cargar notificaciones de hijos vinculados
                try {
                    val userDoc = repo.getDocumentData("users", userId)
                    val roleString = (userDoc?.get("role") as? String) ?: (userDoc?.get("rol") as? String) ?: ""
                    // roleString ya es no-nulo (se normaliza a "" si falta). Evaluar igualdad ignorando mayúsculas.
                    if (roleString.equals("PADRE", ignoreCase = true) || roleString.equals("PARENT", ignoreCase = true)) {
                        // reunir posibles childIds buscando en students/users por parents array, acudienteId o acudienteEmail
                        val childIds = mutableSetOf<String>()
                        val userEmail = auth.currentUser?.email
                        try {
                            val q1 = repo.queryWhereArrayContains("students", "parents", userId)
                            for (d in q1) d["__id"]?.let { childIds.add(it.toString()) }
                        } catch (_: Exception) { }
                        try {
                            val q2 = repo.queryWhereEqual("students", "acudienteId", userId)
                            for (d in q2) d["__id"]?.let { childIds.add(it.toString()) }
                        } catch (_: Exception) { }
                        try {
                            if (!userEmail.isNullOrBlank()) {
                                val q3 = repo.queryWhereEqual("students", "acudienteEmail", userEmail)
                                for (d in q3) d["__id"]?.let { childIds.add(it.toString()) }
                            }
                        } catch (_: Exception) { }
                        try {
                            val q4 = repo.queryWhereArrayContains("users", "parents", userId)
                            for (d in q4) d["__id"]?.let { childIds.add(it.toString()) }
                        } catch (_: Exception) { }
                        try {
                            if (!userEmail.isNullOrBlank()) {
                                val q5 = repo.queryWhereEqual("users", "acudienteEmail", userEmail)
                                for (d in q5) d["__id"]?.let { childIds.add(it.toString()) }
                            }
                        } catch (_: Exception) { }

                        // Para cada childId, leer su colección users/{childId}/notifications
                        for (cid in childIds) {
                            try {
                                val csnap = repo.getSubcollectionDocuments("users", cid, "notifications")
                                var childName: String? = null
                                try {
                                    val udoc = repo.getDocumentData("users", cid)
                                    if (udoc != null) childName = (udoc["fullName"] as? String) ?: (udoc["displayName"] as? String)
                                } catch (_: Exception) { }

                                for (doc in csnap) {
                                    try {
                                        val docId = doc["__id"] as? String ?: continue
                                        val n = Notification(
                                            id = docId,
                                            titulo = doc["titulo"] as? String ?: doc["title"] as? String ?: "",
                                            cuerpo = doc["cuerpo"] as? String ?: doc["body"] as? String ?: "",
                                            remitente = childName ?: (doc["remitente"] as? String ?: ""),
                                            senderName = childName ?: (doc["senderName"] as? String ?: doc["remitente"] as? String),
                                            fechaHora = when (val t = doc["fechaHora"]) {
                                                is com.google.firebase.Timestamp -> t.toDate()
                                                is Date -> t
                                                else -> Date()
                                            },
                                            leida = (doc["leida"] as? Boolean) ?: false,
                                            tipo = doc["type"] as? String ?: doc["tipo"] as? String ?: "",
                                            relatedId = doc["relatedId"] as? String
                                        )
                                        // prefijar id para distinguir notifs de hijos
                                        val mapped = n.copy(id = "child_${cid}_${n.id}")
                                        extraNotifications.add(mapped)
                                    } catch (_: Exception) { }
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
                    // usar helper repo para queries ordenadas (events)
                    val evDocs = repo.queryCollectionOrderedWithOptionalCutoff("events", "date", cutoffTs, 200)
                    for (doc in evDocs) {
                        val docCourse = doc["courseId"] as? String
                        if (!docCourse.isNullOrBlank() && courseIds.isNotEmpty() && !courseIds.contains(docCourse)) continue
                        val id = "event_${doc["__id"]}"
                        val title = doc["title"] as? String ?: doc["titulo"] as? String ?: "Evento"
                        val body = doc["description"] as? String ?: doc["cuerpo"] as? String ?: ""
                        val tsField = doc["date"] ?: doc["fechaHora"]
                        val date = when (tsField) {
                            is com.google.firebase.Timestamp -> tsField.toDate()
                            is Date -> tsField
                            else -> Date()
                        }
                        val owner = doc["ownerId"] as? String ?: doc["creator"] as? String ?: doc["author"] as? String ?: "Calendario"
                        val notif = Notification(
                            id = id,
                            titulo = title,
                            cuerpo = body,
                            remitente = owner,
                            senderName = (doc["senderName"] as? String) ?: owner,
                            fechaHora = date,
                            leida = false,
                            tipo = "evento",
                            relatedId = doc["__id"] as? String
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
                            val annDocs = repo.queryCollectionOrderedWithOptionalCutoff(colName, "createdAt", cutoffTs)
                            Log.d("NotificationsVM", "found ${annDocs.size} docs in $colName")
                            for (doc in annDocs) {
                                // permitir anuncios globales o dirigidos a cursos
                                val targetCourseField = doc["courseId"]
                                var include = true
                                if (targetCourseField != null && courseIds.isNotEmpty()) {
                                    when (targetCourseField) {
                                        is String -> if (!courseIds.contains(targetCourseField)) include = false
                                        is List<*> -> {
                                            val targets = targetCourseField.mapNotNull { it?.toString() }
                                            if (targets.none { courseIds.contains(it) }) include = false
                                        }
                                        else -> {
                                            val asStr = targetCourseField.toString()
                                            if (!courseIds.contains(asStr)) include = false
                                        }
                                    }
                                }
                                if (!include) continue

                                val id = "ann_${doc["__id"]}"
                                val title = doc["title"] as? String ?: doc["titulo"] as? String ?: "Anuncio"
                                val body = doc["body"] as? String ?: doc["cuerpo"] as? String ?: doc["description"] as? String ?: ""
                                val tsField = doc["createdAt"] ?: doc["fechaHora"] ?: doc["date"]
                                val date = when (tsField) {
                                    is com.google.firebase.Timestamp -> tsField.toDate()
                                    is Date -> tsField
                                    else -> Date()
                                }
                                val sender = doc["senderName"] as? String ?: doc["remitente"] as? String ?: doc["author"] as? String ?: "Administración"
                                val notif = Notification(
                                    id = id,
                                    titulo = title,
                                    cuerpo = body,
                                    remitente = sender,
                                    senderName = sender,
                                    fechaHora = date,
                                    leida = false,
                                    tipo = "anuncio",
                                    relatedId = doc["__id"] as? String
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
                    // repo no tiene helper de update directo para subdocumentos, usar createOrUpdateNotification could be added; por ahora ignorar
                    return@launch
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
                                    val childDoc = repo.getDocumentData("users", childId)
                                    if (childDoc != null) {
                                        val title = childDoc["titulo"] as? String ?: childDoc["title"] as? String ?: notif.titulo
                                        val body = childDoc["cuerpo"] as? String ?: childDoc["body"] as? String ?: childDoc["description"] as? String ?: notif.cuerpo
                                        val sender = childDoc["senderName"] as? String ?: childDoc["remitente"] as? String ?: childDoc["author"] as? String ?: notif.remitente
                                        val tsField = childDoc["fechaHora"] ?: childDoc["createdAt"] ?: childDoc["date"]
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
                                            "type" to (childDoc["type"] as? String ?: notif.tipo)
                                        )
                                        // Guardar en users/{userId}/notifications
                                        repo.createNotification(title, body, data["type"] as? String ?: "SYSTEM", "ALL", userId)
                                    } else {
                                        // fallback: crear a partir del objeto notif en memoria
                                        repo.createNotification(notif.titulo, notif.cuerpo, notif.tipo ?: "SYSTEM", "ALL", userId)
                                    }
                                } catch (_: Exception) {
                                    // si falla la lectura del hijo, crear a partir del objeto en memoria
                                    try { repo.createNotification(notif.titulo, notif.cuerpo, notif.tipo ?: "SYSTEM", "ALL", userId) } catch (_: Exception) {}
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
                         try {
                             repo.createNotification(data["titulo"] as? String ?: "", data["cuerpo"] as? String ?: "", data["type"] as? String ?: "SYSTEM", "ALL", userId)
                         } catch (_: Exception) { }
                    }
                } else {
                    // notificación personal existente: actualizar campo 'leida'
                    // repo no proporciona update de subdocument; conservar acceso directo en caso necesario
                    try {
                        // Intentar update con repo: no implementado; dejar como TODO o usar db directo
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(userId).collection("notifications").document(notificationId).update("leida", true)
                    } catch (_: Exception) { }
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
