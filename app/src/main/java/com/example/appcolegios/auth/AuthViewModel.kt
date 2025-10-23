package com.example.appcolegios.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val userPrefs = UserPreferencesRepository(application)

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                fetchUserRole(user.uid)
            } else {
                _authState.value = AuthState.Idle
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    fetchUserRole(user.uid)
                } else {
                    _authState.value = AuthState.Error("Error de autenticación.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun register(email: String, password: String, displayName: String, role: String = "ADMIN") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    // Normalizar rol a mayúsculas antes de guardar
                    val normalizedRole = role.uppercase(Locale.ROOT)
                    val userMap = hashMapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "role" to normalizedRole
                    )
                    firestore.collection("users").document(user.uid).set(userMap).await()
                    _authState.value = AuthState.Idle
                    auth.signOut()
                } else {
                    _authState.value = AuthState.Error("No se pudo crear el usuario.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido en el registro")
            }
        }
    }

    fun resetPassword(email: String, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            if (email.isBlank()) {
                callback(false, "Error: El correo no puede estar vacío.")
                _authState.value = AuthState.Idle
                return@launch
            }
            try {
                auth.sendPasswordResetEmail(email).await()
                callback(true, "Se ha enviado un enlace para restablecer la contraseña a tu correo.")
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                callback(false, "Error: ${e.message ?: "No se pudo enviar el correo de restablecimiento."}")
                _authState.value = AuthState.Idle
            }
        }
    }

    private fun fetchUserRole(userId: String) {
        viewModelScope.launch {
            try {
                val document = firestore.collection("users").document(userId).get().await()
                // Si el documento no tiene rol, asumimos 'ADMIN'
                val role = document.getString("role") ?: "ADMIN"
                val displayName = document.getString("displayName") ?: ""

                // Guardar datos unificados en preferencias
                userPrefs.updateUserData(userId, role, displayName)

                _authState.value = AuthState.Authenticated(userId, role)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("No se pudo obtener el rol del usuario: ${e.localizedMessage ?: ""}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            // Limpiar preferencias unificadas
            userPrefs.updateUserData(null, null, null)
            _authState.value = AuthState.Idle
        }
    }
}
