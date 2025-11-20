package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.FirestoreRepository
import com.example.appcolegios.data.model.AttendanceEntry
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

data class AttendanceUiState(
    val entries: Map<Date, AttendanceEntry> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AttendanceViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repo = FirestoreRepository()

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState

    init {
        loadAttendance()
    }

    private fun loadAttendance() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = AttendanceUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            try {
                val docs = repo.getSubcollectionDocuments("students", userId, "attendance")
                val entries = docs.mapNotNull { doc ->
                    val dateObj = doc["date"] ?: doc["fecha"] ?: return@mapNotNull null
                    val estado = doc["status"] as? String ?: doc["estado"] as? String ?: ""
                    val date = when (dateObj) {
                        is com.google.firebase.Timestamp -> dateObj.toDate()
                        is Date -> dateObj
                        is String -> try { java.text.SimpleDateFormat("yyyy-MM-dd").parse(dateObj) } catch (_: Exception) { null }
                        else -> null
                    }
                    if (date != null) date to AttendanceEntry(fecha = date, estado = when (estado.uppercase()) {
                        "PRESENT" , "PRESENTE" -> com.example.appcolegios.data.model.AttendanceStatus.PRESENTE
                        "ABSENT", "AUSENTE" -> com.example.appcolegios.data.model.AttendanceStatus.AUSENTE
                        "LATE", "TARDE" -> com.example.appcolegios.data.model.AttendanceStatus.TARDE
                        else -> com.example.appcolegios.data.model.AttendanceStatus.PRESENTE
                    }) else null
                }.toMap()
                _uiState.value = AttendanceUiState(entries = entries, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState(isLoading = false, error = e.message)
            }
        }
    }
}
