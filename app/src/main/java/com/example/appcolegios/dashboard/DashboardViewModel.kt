package com.example.appcolegios.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val usersCount: Int = 0,
    val studentsCount: Int = 0,
    val teachersCount: Int = 0,
    val groupsCount: Int = 0
)

class DashboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val usersSnap = db.collection("users").get().await()
                val studentsSnap = db.collection("students").get().await()
                val teachersSnap = db.collection("teachers").get().await()
                val groupsSnap = db.collection("groups").get().await()

                _state.value = DashboardState(
                    loading = false,
                    usersCount = usersSnap.size(),
                    studentsCount = studentsSnap.size(),
                    teachersCount = teachersSnap.size(),
                    groupsCount = groupsSnap.size()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }
}
