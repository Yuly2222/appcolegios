package com.example.appcolegios.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Extensión para crear la instancia de DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class UserDataStore(private val context: Context) {

    companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val DISPLAY_NAME_KEY = stringPreferencesKey("display_name")
        private val ROLE_KEY = stringPreferencesKey("user_role")
    }

    // Guarda los datos del usuario (mantenido para compatibilidad si se llegara a usar)
    suspend fun saveUserData(userId: String, displayName: String, role: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[DISPLAY_NAME_KEY] = displayName
            preferences[ROLE_KEY] = role
        }
    }

    // Limpia los datos del usuario
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
