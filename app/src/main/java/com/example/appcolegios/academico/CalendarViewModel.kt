package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

// Nota: reutiliza la data class CalendarEvent y enum EventType definidas en CalendarScreen.kt

class CalendarViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events

    private val _selectedDayMillis = MutableStateFlow<Long?>(null)
    val selectedDayMillis: StateFlow<Long?> = _selectedDayMillis

    private val _bottomSheetVisible = MutableStateFlow(false)
    val bottomSheetVisible: StateFlow<Boolean> = _bottomSheetVisible

    // Añade o reemplaza un evento en la lista compartida
    fun addOrUpdateEvent(ev: CalendarEvent) {
        viewModelScope.launch {
            val current = _events.value.toMutableList()
            val idx = current.indexOfFirst { it.id == ev.id }
            if (idx >= 0) current[idx] = ev else current.add(ev)
            _events.value = current
        }
    }

    fun removeEvent(eventId: String) {
        viewModelScope.launch {
            val current = _events.value.toMutableList()
            current.removeAll { it.id == eventId }
            _events.value = current
        }
    }

    fun setEvents(list: List<CalendarEvent>) {
        viewModelScope.launch {
            _events.value = list
        }
    }

    fun setSelectedDay(date: Date?) {
        viewModelScope.launch {
            _selectedDayMillis.value = date?.time
            _bottomSheetVisible.value = date != null
        }
    }

    fun setBottomSheetVisible(visible: Boolean) {
        viewModelScope.launch { _bottomSheetVisible.value = visible }
    }

    // Intenta localizar un evento por id (primero en memoria, si no, en firestore) y lo selecciona
    fun selectEventById(eventId: String) {
        viewModelScope.launch {
            if (eventId.isBlank()) return@launch

            // buscar en memoria
            val found = _events.value.find { it.id == eventId }
            if (found != null) {
                _selectedDayMillis.value = found.date.time
                _bottomSheetVisible.value = true
                return@launch
            }

            // intentar cargar desde top-level events
            try {
                val doc = db.collection("events").document(eventId).get().await()
                if (doc.exists()) {
                    val title = doc.getString("title") ?: "Evento"
                    val description = doc.getString("description") ?: ""
                    val ts = doc.get("date")
                    val d = when (ts) {
                        is Timestamp -> ts.toDate()
                        is Date -> ts
                        else -> null
                    }
                    val type = try { EventType.valueOf(doc.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                    if (d != null) {
                        val ev = CalendarEvent(eventId, title, description, d, type, EventSource.GLOBAL, doc.getString("courseId"))
                        addOrUpdateEvent(ev)
                        _selectedDayMillis.value = d.time
                        _bottomSheetVisible.value = true
                        return@launch
                    }
                }

                // intentar users/{uid}/events
                val uid = auth.currentUser?.uid
                if (!uid.isNullOrBlank()) {
                    val ud = db.collection("users").document(uid).collection("events").document(eventId).get().await()
                    if (ud.exists()) {
                        val title = ud.getString("title") ?: "Evento"
                        val description = ud.getString("description") ?: ""
                        val ts = ud.get("date")
                        val d = when (ts) {
                            is Timestamp -> ts.toDate()
                            is Date -> ts
                            else -> null
                        }
                        val type = try { EventType.valueOf(ud.getString("type") ?: EventType.EVENTO.name) } catch (_: Exception) { EventType.EVENTO }
                        if (d != null) {
                            val ev = CalendarEvent(eventId, title, description, d, type, EventSource.USER, uid)
                            addOrUpdateEvent(ev)
                            _selectedDayMillis.value = d.time
                            _bottomSheetVisible.value = true
                            return@launch
                        }
                    }
                }
            } catch (_: Exception) {
                // ignorar fallos silenciosamente; la pantalla puede intentar buscar luego
            }
        }
    }
}
