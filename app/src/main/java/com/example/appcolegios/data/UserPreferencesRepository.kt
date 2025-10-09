package com.example.appcolegios.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    val userData: Flow<UserData> = dataStore.data
        .map { preferences ->
            UserData(
                userId = preferences[PreferencesKeys.USER_ID],
                role = preferences[PreferencesKeys.USER_ROLE],
                name = preferences[PreferencesKeys.USER_NAME]
            )
        }

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
}

data class UserData(
    val userId: String?,
    val role: String?, // mantener string para compatibilidad; usar helper de conversión
    val name: String?
) {
    val roleEnum: Role? get() = Role.fromString(role)
}

suspend fun UserPreferencesRepository.updateUserData(userId: String?, role: Role?) {
    updateUserData(userId, role?.name)
}
