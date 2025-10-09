package com.example.appcolegios.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import java.io.IOException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(private val userPreferencesRepository: UserPreferencesRepository) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun clearState() { _authState.value = AuthState.Idle }

    fun login(emailOrUser: String, password: String) {
        if (_authState.value == AuthState.Loading) return
        // Validaciones locales
        val email = resolveEmail(emailOrUser)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error(AuthErrorType.INVALID_EMAIL, "El correo no es válido.")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthState.Error(AuthErrorType.WRONG_CREDENTIALS, "Usuario o contraseña inválidos.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val userId = result.user?.uid ?: throw FirebaseAuthInvalidUserException("NO_USER", "No existe una cuenta con estos datos.")
                val snap = db.collection("users").document(userId).get().await()
                val roleStr = snap.getString("role")
                val name = snap.getString("name") ?: ""
                val role = Role.fromString(roleStr) ?: Role.ESTUDIANTE
                // Persistir sesión: id, rol y nombre para mostrar
                userPreferencesRepository.updateUserData(userId, role.name, name)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = mapAuthException(e)
            }
        }
    }

    fun register(name: String, email: String, document: String, phone: String, password: String, confirmPassword: String, role: Role = Role.ESTUDIANTE) {
        if (_authState.value == AuthState.Loading) return
        // Validaciones locales
        when {
            name.trim().length < 3 -> { _authState.value = AuthState.Error(AuthErrorType.UNKNOWN, "El nombre debe tener al menos 3 caracteres."); return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { _authState.value = AuthState.Error(AuthErrorType.INVALID_EMAIL, "El correo no es válido."); return }
            document.isBlank() -> { _authState.value = AuthState.Error(AuthErrorType.UNKNOWN, "El documento es obligatorio."); return }
            phone.isBlank() -> { _authState.value = AuthState.Error(AuthErrorType.UNKNOWN, "El teléfono es obligatorio."); return }
            password.length < 6 -> { _authState.value = AuthState.Error(AuthErrorType.WEAK_PASSWORD, "La contraseña debe tener al menos 6 caracteres."); return }
            password != confirmPassword -> { _authState.value = AuthState.Error(AuthErrorType.UNKNOWN, "Las contraseñas no coinciden."); return }
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw FirebaseAuthInvalidUserException("NO_USER", "No existe una cuenta con estos datos.")
                val userData = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "document" to document,
                    "phone" to phone,
                    "role" to role.name
                )
                db.collection("users").document(user.uid).set(userData).await()
                auth.signOut()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = mapAuthException(e)
            }
        }
    }

    fun resetPassword(email: String) {
        if (_authState.value == AuthState.Loading) return
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error(AuthErrorType.INVALID_EMAIL, "El correo no es válido.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = mapAuthException(e)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        viewModelScope.launch { userPreferencesRepository.updateUserData(null, null, null) }
        _authState.value = AuthState.Idle
    }

    private fun resolveEmail(raw: String): String = raw.trim() // extensión futura para resolver usuario

    private fun mapAuthException(e: Exception): AuthState {
        return when (e) {
            is IOException -> AuthState.Error(AuthErrorType.NETWORK, "Sin conexión. Intenta de nuevo.")
            is FirebaseAuthInvalidUserException -> AuthState.Error(AuthErrorType.USER_NOT_FOUND, "No existe una cuenta con estos datos.")
            is FirebaseAuthInvalidCredentialsException -> AuthState.Error(AuthErrorType.WRONG_CREDENTIALS, "Usuario o contraseña inválidos.")
            is FirebaseAuthUserCollisionException -> AuthState.Error(AuthErrorType.EMAIL_IN_USE, "El correo ya está en uso.")
            is FirebaseAuthException -> {
                val code = e.errorCode.lowercase()
                when {
                    code.contains("weak-password") -> AuthState.Error(AuthErrorType.WEAK_PASSWORD, "La contraseña es demasiado débil.")
                    code.contains("invalid-email") -> AuthState.Error(AuthErrorType.INVALID_EMAIL, "El correo no es válido.")
                    else -> AuthState.Error(AuthErrorType.UNKNOWN, e.localizedMessage ?: "Error desconocido")
                }
            }
            else -> AuthState.Error(AuthErrorType.UNKNOWN, e.localizedMessage ?: "Error desconocido")
        }
    }
}
