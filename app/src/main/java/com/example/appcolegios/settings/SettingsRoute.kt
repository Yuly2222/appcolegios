package com.example.appcolegios.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Route composable que conecta el SettingsViewModel con la UI `SettingsScreen`.
 * Mapea eventos del ViewModel a callbacks externos (navegación/acciones).
 */
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = viewModel(),
    // Callbacks opcionales para navegación o acciones que deban manejarse fuera
    onNavigateToChangePassword: () -> Unit = {},
    onRequestPermissionExternal: (PermissionType) -> Unit = {},
    onOpenPolicyExternal: (PolicyType) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Escuchar eventos y mapearlos a callbacks externos
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsViewModel.SettingsEvent.ChangePassword -> onNavigateToChangePassword()
                is SettingsViewModel.SettingsEvent.RequestPermission -> onRequestPermissionExternal(event.type)
                is SettingsViewModel.SettingsEvent.ViewPolicy -> onOpenPolicyExternal(event.type)
                else -> {
                    // Otros eventos pueden manejarse aquí si es necesario
                }
            }
        }
    }

    // Conectar el ViewModel con el layout visual (SettingsScreen)
    SettingsScreen(
        userPrefs = null, // Si quieres pasar un repo, injéctalo aquí
        onLanguageSelected = { viewModel.setLanguage(it) },
        onToggleDarkMode = { viewModel.setDarkMode(it) },
        onFontSizeChanged = { viewModel.setFontSize(it) },
        onTogglePushNotifications = { viewModel.setPushNotifications(it) },
        onToggleAlertType = { type, enabled -> viewModel.toggleAlertType(type, enabled) },
        onToggleSounds = { viewModel.setSounds(it) },
        onToggleVibration = { viewModel.setVibration(it) },

        onChangePassword = { viewModel.changePassword() },
        onToggle2FA = { viewModel.setTwoFa(it) },
        onSignOutOtherDevices = { viewModel.signOutOtherDevices() },
        onRequestPermission = { viewModel.requestPermission(it) },
        onViewPolicy = { viewModel.viewPolicy(it) },

        onChangeRecoveryEmail = { viewModel.changeRecoveryEmail() },
        onViewRoles = { viewModel.viewRoles() },

        onOpenHelpCenter = { viewModel.openHelpCenter() },
        onReportIssue = { viewModel.reportIssue() },
        onSendSuggestion = { viewModel.sendSuggestion() },
        onContactSchool = { viewModel.contactSchool() }
    )
}

