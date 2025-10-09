package com.example.appcolegios.academico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Homework
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class HomeworkUiState(
    val homeworks: List<Homework> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeworkViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeworkUiState())
    val uiState: StateFlow<HomeworkUiState> = _uiState

    init {
        loadHomeworks()
    }

    private fun loadHomeworks() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = HomeworkUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            try {
                val snapshot = db.collection("students").document(userId)
                    .collection("homeworks").orderBy("deadline").get().await()
                val homeworks = snapshot.toObjects(Homework::class.java)
                _uiState.value = HomeworkUiState(homeworks = homeworks, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = HomeworkUiState(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleHomeworkStatus(homeworkId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                db.collection("students").document(userId)
                    .collection("homeworks").document(homeworkId)
                    .update("completada", isCompleted).await()
                // Refresh the list after update
                loadHomeworks()
            } catch (_: Exception) {
                // Optionally handle the error, e.g., show a message
            }
        }
    }
}
