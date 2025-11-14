package com.example.appcolegios.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.appcolegios.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_NAME = stringPreferencesKey("user_name")

        // Nuevas preferencias
        val APP_LANGUAGE = stringPreferencesKey("app_language") // "es" | "en"
        val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val FONT_SIZE = intPreferencesKey("font_size_enum") // 0=Small,1=Normal,2=Large
        val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications_enabled")
    }

    val userData: Flow<UserData> = dataStore.data
        .map { preferences ->
            UserData(
                userId = preferences[PreferencesKeys.USER_ID],
                role = preferences[PreferencesKeys.USER_ROLE],
                name = preferences[PreferencesKeys.USER_NAME]
            )
        }

    // Flows para nuevas preferencias de la app
    val appLanguage: Flow<String?> = dataStore.data.map { it[PreferencesKeys.APP_LANGUAGE] }
    val darkModeEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DARK_MODE] ?: false }
    val fontSizeEnum: Flow<Int> = dataStore.data.map { it[PreferencesKeys.FONT_SIZE] ?: 1 }
    val pushNotificationsEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.PUSH_NOTIFICATIONS] ?: true }

    // Actualiza userId, role y displayName
    suspend fun updateUserData(userId: String?, role: String?, name: String?) {
        dataStore.edit { preferences ->
            if (userId == null) {
                preferences.remove(PreferencesKeys.USER_ID)
            } else {
                preferences[PreferencesKeys.USER_ID] = userId
            }
            if (role == null) {
                preferences.remove(PreferencesKeys.USER_ROLE)
            } else {
                preferences[PreferencesKeys.USER_ROLE] = role
            }
            if (name == null) {
                preferences.remove(PreferencesKeys.USER_NAME)
            } else {
                preferences[PreferencesKeys.USER_NAME] = name
            }
        }
    }

    // Setters para nuevas preferencias
    suspend fun setAppLanguage(lang: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.APP_LANGUAGE] = lang }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun setFontSizeEnum(value: Int) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.FONT_SIZE] = value }
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.PUSH_NOTIFICATIONS] = enabled }
    }

    // Limpia todos los datos del usuario al cerrar sesión
    suspend fun clearAllUserData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class UserData(
    val userId: String?,
    val role: String?, // mantener string para compatibilidad; usar helper de conversión
    val name: String?
) {
    // Si no hay rol (null o vacío) retornamos null para evitar asumir ADMIN por defecto
    val roleEnum: Role? get() = when {
        role.isNullOrBlank() -> null
        else -> Role.fromString(role)
    }
}
