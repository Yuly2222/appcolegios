package com.example.appcolegios.mensajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Message
import com.example.appcolegios.data.model.MessageType
import com.example.appcolegios.data.model.MessageStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var messagesListener: ListenerRegistration? = null
    private var currentConversationId: String? = null

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = ChatUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            if (conversationId.isBlank() || conversationId.equals("unknown", ignoreCase = true)) {
                // Evitamos crear listeners innecesarios
                _uiState.value = ChatUiState(messages = emptyList(), isLoading = false, error = null)
                return@launch
            }

            if (currentConversationId == conversationId && messagesListener != null) {
                // Ya tenemos un listener activo para esta conversación
                return@launch
            }

            // Cambiamos de conversación: limpiar listener anterior
            messagesListener?.remove()
            messagesListener = null
            currentConversationId = conversationId
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val conversationRef = db.collection("chats").document(conversationId).collection("messages")
            messagesListener = conversationRef
                .orderBy("fechaHora", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _uiState.value = ChatUiState(isLoading = false, error = error.message)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.toObjects(Message::class.java)
                        _uiState.value = ChatUiState(messages = messages, isLoading = false)
                    } else {
                        _uiState.value = ChatUiState(messages = emptyList(), isLoading = false)
                    }
                }
        }
    }

    fun sendMessage(text: String, conversationId: String, toId: String) {
        viewModelScope.launch {
            val fromId = auth.currentUser?.uid ?: return@launch
            val message = Message(
                id = UUID.randomUUID().toString(),
                fromId = fromId,
                toId = toId,
                texto = text,
                fechaHora = Date(),
                tipo = MessageType.TEXTO,
                estado = MessageStatus.ENVIADO
            )
            try {
                db.collection("chats").document(conversationId)
                    .collection("messages").add(message).await()
            } catch (_: Exception) {
                // Se podría loggear el error o actualizar estado
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        messagesListener = null
    }
}
