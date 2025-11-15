package com.example.appcolegios.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de ajustes. Mantiene el estado UI y persiste cambios en
 * `UserPreferencesRepository` cuando se proporciona.
 */
class SettingsViewModel(
    private val repo: UserPreferencesRepository? = null
) : ViewModel() {

    data class SettingsUiState(
        val language: String = "es",
        val darkMode: Boolean = false,
        val fontSize: FontSize = FontSize.Normal,
        val pushEnabled: Boolean = true,

        val alertTasks: Boolean = true,
        val alertGrades: Boolean = true,
        val alertMessages: Boolean = true,
        val alertEvents: Boolean = true,

        val soundsEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,

        val twoFaEnabled: Boolean = false
    )

    sealed class SettingsEvent {
        object ChangePassword : SettingsEvent()
        object SignOutOtherDevices : SettingsEvent()
        data class RequestPermission(val type: PermissionType) : SettingsEvent()
        data class ViewPolicy(val type: PolicyType) : SettingsEvent()
        object ChangeRecoveryEmail : SettingsEvent()
        object ViewRoles : SettingsEvent()
        object OpenHelpCenter : SettingsEvent()
        object ReportIssue : SettingsEvent()
        object SendSuggestion : SettingsEvent()
        object ContactSchool : SettingsEvent()
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    init {
        // Si hay repo, sincronizar valores iniciales
        if (repo != null) {
            viewModelScope.launch {
                try {
                    repo.appLanguage.collect { lang ->
                        if (lang != null) _uiState.update { it.copy(language = lang) }
                    }
                } catch (_: Exception) { /* tolerante a errores de I/O */ }
            }

            viewModelScope.launch {
                try {
                    repo.darkModeEnabled.collect { enabled ->
                        _uiState.update { it.copy(darkMode = enabled) }
                    }
                } catch (_: Exception) { }
            }

            viewModelScope.launch {
                try {
                    repo.fontSizeEnum.collect { enumVal ->
                        val fs = when (enumVal) { 0 -> FontSize.Small; 2 -> FontSize.Large; else -> FontSize.Normal }
                        _uiState.update { it.copy(fontSize = fs) }
                    }
                } catch (_: Exception) { }
            }

            viewModelScope.launch {
                try {
                    repo.pushNotificationsEnabled.collect { enabled ->
                        _uiState.update { it.copy(pushEnabled = enabled) }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    // -- Mutadores que actualizan estado y persisten cuando hay repo --
    fun setLanguage(lang: String) {
        _uiState.update { it.copy(language = lang) }
        viewModelScope.launch { repo?.setAppLanguage(lang) }
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(darkMode = enabled) }
        viewModelScope.launch { repo?.setDarkMode(enabled) }
    }

    fun setFontSize(fs: FontSize) {
        val enumVal = when (fs) { FontSize.Small -> 0; FontSize.Large -> 2; else -> 1 }
        _uiState.update { it.copy(fontSize = fs) }
        viewModelScope.launch { repo?.setFontSizeEnum(enumVal) }
    }

    fun setPushNotifications(enabled: Boolean) {
        _uiState.update { it.copy(pushEnabled = enabled) }
        viewModelScope.launch { repo?.setPushNotificationsEnabled(enabled) }
    }

    fun toggleAlertType(type: AlertType, enabled: Boolean) {
        _uiState.update {
            when (type) {
                AlertType.Tasks -> it.copy(alertTasks = enabled)
                AlertType.Grades -> it.copy(alertGrades = enabled)
                AlertType.Messages -> it.copy(alertMessages = enabled)
                AlertType.Events -> it.copy(alertEvents = enabled)
            }
        }
        // Si se quiere persistir este detalle, implementar métodos en repo y llamarlos aquí
    }

    fun setSounds(enabled: Boolean) {
        _uiState.update { it.copy(soundsEnabled = enabled) }
    }

    fun setVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setTwoFa(enabled: Boolean) {
        _uiState.update { it.copy(twoFaEnabled = enabled) }
        viewModelScope.launch { /* repo?.setTwoFa(enabled) si se añade al repo */ }
    }

    // -- Eventos/UI actions que pueden mapearse a navegación o permisos --
    fun changePassword() = viewModelScope.launch { _events.emit(SettingsEvent.ChangePassword) }
    fun signOutOtherDevices() = viewModelScope.launch { _events.emit(SettingsEvent.SignOutOtherDevices) }
    fun requestPermission(type: PermissionType) = viewModelScope.launch { _events.emit(SettingsEvent.RequestPermission(type)) }
    fun viewPolicy(type: PolicyType) = viewModelScope.launch { _events.emit(SettingsEvent.ViewPolicy(type)) }
    fun changeRecoveryEmail() = viewModelScope.launch { _events.emit(SettingsEvent.ChangeRecoveryEmail) }
    fun viewRoles() = viewModelScope.launch { _events.emit(SettingsEvent.ViewRoles) }
    fun openHelpCenter() = viewModelScope.launch { _events.emit(SettingsEvent.OpenHelpCenter) }
    fun reportIssue() = viewModelScope.launch { _events.emit(SettingsEvent.ReportIssue) }
    fun sendSuggestion() = viewModelScope.launch { _events.emit(SettingsEvent.SendSuggestion) }
    fun contactSchool() = viewModelScope.launch { _events.emit(SettingsEvent.ContactSchool) }
}

