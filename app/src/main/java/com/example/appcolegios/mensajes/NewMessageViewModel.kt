package com.example.appcolegios.mensajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SimpleUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)

data class NewMessageUiState(
    val users: List<SimpleUser> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class NewMessageViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(NewMessageUiState())
    val uiState: StateFlow<NewMessageUiState> = _uiState

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            val currentId = auth.currentUser?.uid
            if (currentId == null) {
                _uiState.value = NewMessageUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Primero intenta desde publicProfiles
                val pubSnap = try { db.collection("publicProfiles").get().await() } catch (e: Exception) { null }
                val listFromPublic = pubSnap?.documents?.mapNotNull { d ->
                    val id = d.id
                    if (id == currentId) return@mapNotNull null
                    val name = d.getString("name") ?: d.getString("displayName") ?: d.getString("email") ?: id
                    val avatar = d.getString("avatarUrl")
                    SimpleUser(id = id, name = name, avatarUrl = avatar)
                } ?: emptyList()

                val sourceList = if (listFromPublic.isNotEmpty()) listFromPublic else run {
                    val userSnap = db.collection("users").get().await()
                    userSnap.documents.mapNotNull { d ->
                        val id = d.id
                        if (id == currentId) return@mapNotNull null
                        val name = d.getString("name") ?: d.getString("displayName") ?: d.getString("email") ?: id
                        val avatar = d.getString("avatarUrl")
                        SimpleUser(id = id, name = name, avatarUrl = avatar)
                    }
                }

                val list = sourceList.sortedBy { it.name.lowercase() }
                _uiState.value = NewMessageUiState(users = list, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = NewMessageUiState(isLoading = false, error = e.message)
            }
        }
    }
}
