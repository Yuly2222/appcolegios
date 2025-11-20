package com.example.appcolegios.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val repo: FirestoreRepository = FirestoreRepository()
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

    // Añadimos groupId opcional para asignar al registrar (si aplica)
    fun register(email: String, password: String, displayName: String, role: String = "ADMIN", groupId: String? = null) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    // Normalizar rol a mayúsculas antes de guardar
                    val normalizedRole = role.uppercase(Locale.ROOT)
                    // Crear documento users/{uid} usando el repositorio
                    // Use unified helper to create users/{uid} and role document, and ensure groups/Grades
                    repo.createUserAndRole(
                        uid = user.uid,
                        email = email,
                        fullName = displayName,
                        role = normalizedRole,
                        groupId = groupId
                    )
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
                val userDoc = repo.getDocumentData("users", userId)
                var role = userDoc?.get("role") as? String ?: ""
                var displayName = userDoc?.get("fullName") as? String ?: ""

                if (role.isBlank() && displayName.isBlank()) {
                    // Si no existe users/{uid} podemos intentar localizar en students/teachers/parents
                    val collections = listOf("students", "teachers", "parents")
                    for (coll in collections) {
                        val docMap = repo.getDocumentData(coll, userId)
                        if (docMap != null) {
                            role = when (coll) {
                                "students" -> "STUDENT"
                                "teachers" -> "TEACHER"
                                "parents" -> "PARENT"
                                else -> "ADMIN"
                            }
                            displayName = docMap["fullName"] as? String ?: (docMap["name"] as? String ?: "")
                            break
                        }
                    }
                    // Si aún no hallamos por uid, intentar buscar por email
                    if (displayName.isBlank()) {
                        try {
                            val userRecord = auth.currentUser
                            val email = userRecord?.email
                            if (!email.isNullOrBlank()) {
                                for (coll in collections) {
                                    val docMap = repo.queryDocumentByEmail(coll, email)
                                    if (docMap != null) {
                                        role = docMap["role"] as? String ?: role
                                        displayName = docMap["fullName"] as? String ?: (docMap["name"] as? String ?: displayName)
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
