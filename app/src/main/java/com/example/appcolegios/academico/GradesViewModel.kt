package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.FirestoreRepository
import com.example.appcolegios.data.model.Grade
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GradesUiState(
    val grades: List<Grade> = emptyList(),
    val overallAverage: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class GradesViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repo = FirestoreRepository()

    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState

    init {
        loadGrades()
    }

    private fun loadGrades() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = GradesUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            try {
                val gradeDocs = repo.getSubcollectionDocuments("students", userId, "grades")
                val grades = gradeDocs.map { doc ->
                    Grade(
                        materiaId = doc["__id"] as? String ?: "",
                        materia = doc["subject"] as? String ?: doc["materia"] as? String ?: "",
                        periodo = (doc["period"] as? String)?.toIntOrNull() ?: (doc["periodo"] as? Number)?.toInt() ?: 0,
                        calificacion = (doc["score"] as? Number)?.toDouble() ?: (doc["calificacion"] as? Number)?.toDouble() ?: 0.0,
                        ponderacion = (doc["maxScore"] as? Number)?.toDouble() ?: (doc["ponderacion"] as? Number)?.toDouble() ?: 1.0
                    )
                }

                val weightedSum = grades.sumOf { (if (it.ponderacion > 0) it.calificacion * it.ponderacion else it.calificacion) }
                val weightTotal = grades.sumOf { if (it.ponderacion > 0) it.ponderacion else 1.0 }
                val average = if (grades.isNotEmpty() && weightTotal > 0) weightedSum / weightTotal else 0.0
                _uiState.value = GradesUiState(grades = grades, overallAverage = average, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = GradesUiState(isLoading = false, error = e.message)
            }
        }
    }
}
