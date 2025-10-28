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

    @Suppress("unused")
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    // Verificar email
                    if (!user.isEmailVerified) {
                        auth.signOut()
                        _authState.value = AuthState.Error("Por favor verifica tu correo antes de iniciar sesión.")
                        return@launch
                    }
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
                    // enviar email de verificación
                    try { user.sendEmailVerification().await() } catch (_: Exception) {}
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
                var role = document.getString("role") ?: ""
                var displayName = document.getString("displayName") ?: ""

                // Si no encontramos documento en users, intentar buscar por email en colecciones específicas
                if (role.isBlank() && displayName.isBlank()) {
                    // intentar localizar por uid en colecciones conocidas
                    val collections = listOf("students", "teachers", "parents", "admins")
                    for (coll in collections) {
                        val doc = firestore.collection(coll).document(userId).get().await()
                        if (doc.exists()) {
                            role = doc.getString("role") ?: when (coll) {
                                "students" -> "ESTUDIANTE"
                                "teachers" -> "DOCENTE"
                                "parents" -> "PADRE"
                                "admins" -> "ADMIN"
                                else -> "ADMIN"
                            }
                            displayName = (doc.getString("nombres") ?: doc.getString("name")) ?: ""
                            break
                        }
                    }
                    // Si aún no hallamos por uid, intentar buscar por email si FirebaseAuth tiene usuario con ese uid
                    if (displayName.isBlank()) {
                        try {
                            val userRecord = auth.currentUser
                            // no siempre disponible; intentar buscar por email en colecciones
                            val email = userRecord?.email
                            if (!email.isNullOrBlank()) {
                                for (coll in listOf("students", "teachers", "parents", "admins")) {
                                    val query = firestore.collection(coll).whereEqualTo("email", email).limit(1).get().await()
                                    if (!query.isEmpty) {
                                        val d = query.documents[0]
                                        role = d.getString("role") ?: role
                                        displayName = (d.getString("nombres") ?: d.getString("name")) ?: displayName
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (role.isBlank()) role = "ADMIN"
                if (displayName.isBlank()) displayName = ""

                // Guardar datos unificados en preferencias
                userPrefs.updateUserData(userId, role, displayName)

                _authState.value = AuthState.Authenticated(userId, role)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("No se pudo obtener el rol del usuario: ${e.localizedMessage ?: ""}")
            }
        }
    }

    @Suppress("unused")
    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            // Limpiar preferencias unificadas
            userPrefs.updateUserData(null, null, null)
            _authState.value = AuthState.Idle
        }
    }
}
