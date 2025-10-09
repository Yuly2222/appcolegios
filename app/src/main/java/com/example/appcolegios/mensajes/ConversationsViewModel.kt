package com.example.appcolegios.mensajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
 import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date

// A simplified representation for the conversation list
data class Conversation(
    val id: String, // Typically the other user's ID
    val otherUserName: String,
    val lastMessage: String,
    val timestamp: Date,
    val otherUserAvatarUrl: String? = null
)

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ConversationsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = ConversationsUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }
            _uiState.value = ConversationsUiState(
                isLoading = false,
                conversations = createMockConversations()
            )
        }
    }

    private fun createMockConversations(): List<Conversation> = listOf(
        Conversation("1", "Juan Pérez", "Hola, ¿cómo estás?", Date(), null),
        Conversation("2", "Ana García", "Revisa la tarea de matemáticas.", Date(System.currentTimeMillis() - 1000 * 60 * 5)),
        Conversation("3", "Luis Rodríguez", "Nos vemos en la reunión de padres.", Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2)),
        Conversation("4", "María López", "Gracias por la ayuda.", Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)),
        Conversation("5", "Carlos Martínez", "Perfecto, lo tendré en cuenta.", Date(System.currentTimeMillis() - 1000 * 60 * 60 * 48)),
        Conversation("6", "Sofía Hernández", "¿Recibiste el correo?", Date(System.currentTimeMillis() - 1000 * 60 * 60 * 72))
    )
}
