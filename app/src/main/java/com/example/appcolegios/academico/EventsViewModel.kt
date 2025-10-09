package com.example.appcolegios.academico

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Event
import com.example.appcolegios.data.model.EventCategory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EventsUiState(
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class EventsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("events")
                    .orderBy("fechaHora", Query.Direction.ASCENDING)
                    .get().await()
                val events = snapshot.toObjects(Event::class.java)
                val finalList = if (events.isEmpty()) loadFallback() else events
                _uiState.value = EventsUiState(events = finalList, isLoading = false)
            } catch (e: Exception) {
                // fallback
                val fallback = loadFallback()
                _uiState.value = EventsUiState(events = fallback, isLoading = false, error = e.message)
            }
        }
    }

    private fun loadFallback(): List<Event> {
        return try {
            val input = getApplication<Application>().assets.open("events.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(input)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rawIcon = o.optString("icon").ifBlank { null }
                Event(
                    id = o.getString("id"),
                    titulo = o.getString("titulo"),
                    fechaHora = sdf.parse(o.getString("fechaHora")) ?: Date(),
                    categoria = EventCategory.valueOf(o.getString("categoria")),
                    descripcion = o.getString("descripcion"),
                    icon = rawIcon
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
