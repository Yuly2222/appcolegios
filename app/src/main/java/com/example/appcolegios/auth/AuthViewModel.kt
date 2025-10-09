package com.example.appcolegios.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

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

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    // Guardar información adicional del usuario en Firestore
                    val userMap = hashMapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "role" to "student" // Rol por defecto
                    )
                    firestore.collection("users").document(user.uid).set(userMap).await()
                    // Considerar el registro como exitoso, pero dirigir a login
                    // Para cumplir el requisito, no cambiaremos el estado a Authenticated aquí.
                    // En su lugar, podríamos querer un estado diferente como "RegistrationSuccess"
                    // o simplemente resetear a Idle y confiar en la navegación.
                     _authState.value = AuthState.Idle // O un nuevo estado para indicar éxito de registro
                     auth.signOut() // Cerrar sesión para forzar el login
                } else {
                    _authState.value = AuthState.Error("No se pudo crear el usuario.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error desconocido en el registro")
            }
        }
    }

     private fun fetchUserRole(userId: String) {
        viewModelScope.launch {
            try {
                val document = firestore.collection("users").document(userId).get().await()
                val role = document.getString("role") ?: "student" // Rol por defecto si no existe
                _authState.value = AuthState.Authenticated(userId, role)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("No se pudo obtener el rol del usuario.")
            }
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
        // Aquí también se debería limpiar el DataStore
    }
}

