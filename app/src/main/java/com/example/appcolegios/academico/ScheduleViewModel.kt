package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.FirestoreRepository
import com.example.appcolegios.data.model.ClassSession
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val schedule: Map<Int, List<ClassSession>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ScheduleViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repo = FirestoreRepository()

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
                val docs = repo.getSubcollectionDocuments("students", userId, "schedule")
                val scheduleList = docs.mapNotNull { doc ->
                    try {
                        ClassSession(
                            dayOfWeek = (doc["dayOfWeek"] as? Number)?.toInt() ?: (doc["dia"] as? Number)?.toInt() ?: 0,
                            subject = doc["subject"] as? String ?: doc["materia"] as? String ?: "",
                            teacher = doc["teacher"] as? String ?: doc["profesor"] as? String ?: "",
                            startTime = doc["startTime"] as? String ?: doc["horaInicio"] as? String ?: "",
                            endTime = doc["endTime"] as? String ?: doc["horaFin"] as? String ?: "",
                            classroom = doc["classroom"] as? String ?: doc["aula"] as? String ?: ""
                        )
                    } catch (_: Exception) { null }
                }
                val scheduleMap = scheduleList.groupBy { it.dayOfWeek }
                _uiState.value = ScheduleUiState(schedule = scheduleMap, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState(isLoading = false, error = e.message)
            }
        }
    }
}
