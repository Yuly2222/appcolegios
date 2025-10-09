package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Grade
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class GradesUiState(
    val grades: List<Grade> = emptyList(),
    val overallAverage: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class GradesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
                val snapshot = db.collection("students").document(userId)
                    .collection("grades").get().await()
                val grades = snapshot.toObjects(Grade::class.java)
                val average = if (grades.isNotEmpty()) {
                    grades.sumOf { it.calificacion * it.ponderacion } / grades.sumOf { it.ponderacion }
                } else {
                    0.0
                }
                _uiState.value = GradesUiState(grades = grades, overallAverage = average, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = GradesUiState(isLoading = false, error = e.message)
            }
        }
    }
}

