package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.AttendanceEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class AttendanceUiState(
    val entries: Map<Date, AttendanceEntry> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AttendanceViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
                val snapshot = db.collection("students").document(userId)
                    .collection("attendance").get().await()
                val entries = snapshot.toObjects(AttendanceEntry::class.java)
                    .associateBy { it.fecha }
                _uiState.value = AttendanceUiState(entries = entries, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState(isLoading = false, error = e.message)
            }
        }
    }
}

