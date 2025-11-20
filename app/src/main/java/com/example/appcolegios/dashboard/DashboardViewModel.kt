package com.example.appcolegios.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val usersCount: Int = 0,
    val studentsCount: Int = 0,
    val teachersCount: Int = 0,
    val groupsCount: Int = 0
)

class DashboardViewModel : ViewModel() {
    private val repo = FirestoreRepository()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val usersCount = repo.countCollectionDocuments("users")
                val studentsCount = repo.countCollectionDocuments("students")
                val teachersCount = repo.countCollectionDocuments("teachers")
                val groupsCount = repo.countCollectionDocuments("groups")

                _state.value = DashboardState(
                    loading = false,
                    usersCount = usersCount,
                    studentsCount = studentsCount,
                    teachersCount = teachersCount,
                    groupsCount = groupsCount
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }
}
