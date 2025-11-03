package com.example.appcolegios.notificaciones

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

                val snapshot = query.get().await()

                val notifications = snapshot.documents.mapNotNull { doc ->
                    val n = doc.toObject(Notification::class.java)
                    n?.copy(id = doc.id)
                }
                val groupedNotifications = groupNotificationsByDate(notifications)
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
                db.collection("users").document(userId)
                    .collection("notifications").document(notificationId)
                    .update("leida", true).await()
            } catch (_: Exception) {
                // En caso de error remoto, podríamos revertir local, pero lo omitimos por simplicidad
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
