package com.example.appcolegios.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.appcolegios.data.UserPreferencesRepository
import kotlinx.coroutines.launch

/**
 * Pantalla de Ajustes modular y sin lógica de backend por defecto.
 * Si se proporciona `userPrefs`, se leerán/guardarán las preferencias en DataStore.
 */

@Composable
fun SettingsScreen(
    userPrefs: UserPreferencesRepository? = null,

    // -- Callbacks legacy (compatibilidad si no se pasa userPrefs) --
    onLanguageSelected: (String) -> Unit = { _ -> }, // "es" | "en"
    onToggleDarkMode: (Boolean) -> Unit = { _ -> },
    onFontSizeChanged: (FontSize) -> Unit = { _ -> },
    onTogglePushNotifications: (Boolean) -> Unit = { _ -> },
    onToggleAlertType: (AlertType, Boolean) -> Unit = { _, _ -> },
    onToggleSounds: (Boolean) -> Unit = { _ -> },
    onToggleVibration: (Boolean) -> Unit = { _ -> },

    // -- Security & Privacy callbacks --
    onChangePassword: () -> Unit = {},
    onToggle2FA: (Boolean) -> Unit = { _ -> },
    onSignOutOtherDevices: () -> Unit = {},
    onRequestPermission: (PermissionType) -> Unit = { _ -> },
    onViewPolicy: (PolicyType) -> Unit = { _ -> },

    // -- Account Management callbacks --
    onChangeRecoveryEmail: () -> Unit = {},
    onViewRoles: () -> Unit = {},

    // -- Help & Support callbacks --
    onOpenHelpCenter: () -> Unit = {},
    onReportIssue: () -> Unit = {},
    onSendSuggestion: () -> Unit = {},
    onContactSchool: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Si se proporciona userPrefs, leer flujos y mantenerlos como estado
    val repoLanguage by userPrefs?.appLanguage?.collectAsState(initial = "es") ?: remember { mutableStateOf("es") }
    val repoDarkMode by userPrefs?.darkModeEnabled?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val repoFontEnum by userPrefs?.fontSizeEnum?.collectAsState(initial = 1) ?: remember { mutableStateOf(1) }
    val repoPushEnabled by userPrefs?.pushNotificationsEnabled?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }

    // Local state (se inicializa desde repo si existe)
    var selectedLanguage by remember { mutableStateOf(repoLanguage ?: "es") }
    var darkModeEnabled by remember { mutableStateOf(repoDarkMode) }
    var fontSizeState by remember { mutableStateOf(when (repoFontEnum) { 0 -> FontSize.Small; 2 -> FontSize.Large; else -> FontSize.Normal }) }
    var pushNotificationsEnabled by remember { mutableStateOf(repoPushEnabled) }

    var alertTasks by remember { mutableStateOf(true) }
    var alertGrades by remember { mutableStateOf(true) }
    var alertMessages by remember { mutableStateOf(true) }
    var alertEvents by remember { mutableStateOf(true) }
    var soundsEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    var twoFaEnabled by remember { mutableStateOf(false) }

    // Sincronizar cambios de repo -> estado local cuando cambian (por ejemplo por otra pantalla)
    LaunchedEffect(repoLanguage) { selectedLanguage = repoLanguage ?: "es" }
    LaunchedEffect(repoDarkMode) { darkModeEnabled = repoDarkMode }
    LaunchedEffect(repoFontEnum) { fontSizeState = when (repoFontEnum) { 0 -> FontSize.Small; 2 -> FontSize.Large; else -> FontSize.Normal } }
    LaunchedEffect(repoPushEnabled) { pushNotificationsEnabled = repoPushEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Section: Preferencias de la aplicación
        SectionCard(title = "Preferencias de la aplicación") {
            // Idioma
            Text("Selección de idioma", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButtonGroup(
                    options = listOf("es" to "Español", "en" to "English"),
                    selectedKey = selectedLanguage,
                    onSelect = { key ->
                        selectedLanguage = key
                        // Persistir
                        if (userPrefs != null) {
                            scope.launch { userPrefs.setAppLanguage(key) }
                        } else {
                            onLanguageSelected(key)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Modo oscuro / claro
            PreferenceToggle(
                label = "Modo oscuro",
                checked = darkModeEnabled,
                onCheckedChange = { checked ->
                    darkModeEnabled = checked
                    if (userPrefs != null) {
                        scope.launch { userPrefs.setDarkMode(checked) }
                    } else onToggleDarkMode(checked)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tamaño de letra / accesibilidad
            Text("Accesibilidad visual", style = MaterialTheme.typography.titleMedium)
            FontSizeSelector(current = fontSizeState, onSelect = { fs ->
                fontSizeState = fs
                val enumVal = when (fs) { FontSize.Small -> 0; FontSize.Large -> 2; else -> 1 }
                if (userPrefs != null) {
                    scope.launch { userPrefs.setFontSizeEnum(enumVal) }
                } else onFontSizeChanged(fs)
            })

            Spacer(modifier = Modifier.height(8.dp))

            // Notificaciones
            Text("Gestión de notificaciones", style = MaterialTheme.typography.titleMedium)
            PreferenceToggle("Notificaciones push", pushNotificationsEnabled) { checked ->
                pushNotificationsEnabled = checked
                if (userPrefs != null) {
                    scope.launch { userPrefs.setPushNotificationsEnabled(checked) }
                } else onTogglePushNotifications(checked)
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                // Tipos de alertas (solo UI, persistir según necesidad)
                PreferenceToggle("Tareas", alertTasks) { checked -> alertTasks = checked; onToggleAlertType(AlertType.Tasks, checked) }
                PreferenceToggle("Calificaciones", alertGrades) { checked -> alertGrades = checked; onToggleAlertType(AlertType.Grades, checked) }
                PreferenceToggle("Mensajes", alertMessages) { checked -> alertMessages = checked; onToggleAlertType(AlertType.Messages, checked) }
                PreferenceToggle("Eventos", alertEvents) { checked -> alertEvents = checked; onToggleAlertType(AlertType.Events, checked) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sonidos y vibración
            PreferenceToggle("Sonidos", soundsEnabled) { checked -> soundsEnabled = checked; onToggleSounds(checked) }
            PreferenceToggle("Vibración", vibrationEnabled) { checked -> vibrationEnabled = checked; onToggleVibration(checked) }
        }

        // Section: Seguridad y privacidad
        SectionCard(title = "Seguridad y privacidad") {
            // Cambiar contraseña
            ActionRow(label = "Cambiar contraseña", onClick = onChangePassword)

            // 2FA
            PreferenceToggle("Autenticación en dos pasos (2FA)", twoFaEnabled) { checked -> twoFaEnabled = checked; onToggle2FA(checked) }

            // Cerrar sesión en otros dispositivos
            ActionRow(label = "Cerrar sesión en otros dispositivos", onClick = onSignOutOtherDevices)

            Spacer(modifier = Modifier.height(8.dp))

            // Permisos de privacidad
            Text("Permisos de privacidad", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                PermissionRow("Cámara") { onRequestPermission(PermissionType.Camera) }
                PermissionRow("Archivos / Almacenamiento") { onRequestPermission(PermissionType.Storage) }
                PermissionRow("Ubicación (si está habilitada por el acudiente)") { onRequestPermission(PermissionType.Location) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ver políticas
            Text("Ver políticas", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                LinkRow("Política de privacidad") { onViewPolicy(PolicyType.Privacy) }
                LinkRow("Términos y condiciones") { onViewPolicy(PolicyType.Terms) }
                LinkRow("Normas de convivencia digital") { onViewPolicy(PolicyType.Rules) }
            }
        }

        // Section: Gestión de cuenta
        SectionCard(title = "Gestión de cuenta") {
            ActionRow(label = "Cambiar correo de recuperación", onClick = onChangeRecoveryEmail)
            ActionRow(label = "Ver roles asociados", onClick = onViewRoles)
        }

        // Section: Ayuda y soporte
        SectionCard(title = "Ayuda y soporte") {
            ActionRow(label = "Centro de ayuda / FAQs", onClick = onOpenHelpCenter)
            ActionRow(label = "Reportar un problema", onClick = onReportIssue)
            ActionRow(label = "Enviar sugerencias", onClick = onSendSuggestion)
            ActionRow(label = "Contacto del colegio", onClick = onContactSchool)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -------------------- Reusable composables y tipos --------------------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionRow(label: String, onRequest: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onRequest) { Text("Solicitar") }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text("Ver") }
    }
}

@Composable
private fun RadioButtonGroup(options: List<Pair<String, String>>, selectedKey: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = key == selectedKey, onClick = { onSelect(key) })
                Text(label)
            }
        }
    }
}

@Composable
private fun FontSizeSelector(current: FontSize, onSelect: (FontSize) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FontSize.entries.forEach { size ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = size == current, onClick = { onSelect(size) })
                Text(size.displayName)
            }
        }
    }
}

// Tipos auxiliares simples para callbacks
enum class FontSize(val displayName: String) { Small("Pequeña"), Normal("Normal"), Large("Fuente grande") }
enum class AlertType { Tasks, Grades, Messages, Events }
enum class PermissionType { Camera, Storage, Location }
enum class PolicyType { Privacy, Terms, Rules }
