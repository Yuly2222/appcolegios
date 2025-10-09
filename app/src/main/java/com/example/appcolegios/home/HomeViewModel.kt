package com.example.appcolegios.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val name: String? = null,
    val role: Role? = null,
    val unreadNotifications: Int = 0,
    val unreadMessages: Int = 0,
    val pagosEnConsideracion: Boolean = false,
    val error: String? = null
)

class HomeViewModel(private val userPrefs: UserPreferencesRepository) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui

    init {
        observeUser()
        simulateBadges()
    }

    private fun observeUser() {
        viewModelScope.launch {
            userPrefs.userData.collect { data ->
                _ui.update { it.copy(role = data.roleEnum, name = data.name ?: "", loading = false) }
            }
        }
    }

    // Simulación de conteos y pagos hasta integrar backend real
    private fun simulateBadges() {
        viewModelScope.launch {
            while (true) {
                delay(5000)
                _ui.update { state ->
                    state.copy(
                        unreadNotifications = (0..5).random(),
                        unreadMessages = (0..3).random(),
                        pagosEnConsideracion = listOf(true, false).random()
                    )
                }
            }
        }
    }
}
