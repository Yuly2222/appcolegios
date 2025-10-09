package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.ClassSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ScheduleUiState(
    val schedule: Map<Int, List<ClassSession>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ScheduleViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = ScheduleUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            try {
                // Assuming schedule is stored under the student's document
                val snapshot = db.collection("students").document(userId)
                    .collection("schedule").orderBy("startTime").get().await()
                val scheduleList = snapshot.toObjects(ClassSession::class.java)
                val scheduleMap = scheduleList.groupBy { it.dayOfWeek }
                _uiState.value = ScheduleUiState(schedule = scheduleMap, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState(isLoading = false, error = e.message)
            }
        }
    }
}

