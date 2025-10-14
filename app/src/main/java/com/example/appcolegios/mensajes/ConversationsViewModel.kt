package com.example.appcolegios.mensajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date

// A simplified representation for the conversation list
data class Conversation(
    val id: String, // other user's ID
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
    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState

    private var inboxListener: ListenerRegistration? = null

    init {
        observeInbox()
    }

    private fun observeInbox() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = ConversationsUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            inboxListener?.remove()
            inboxListener = db.collection("users").document(userId)
                .collection("inbox")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _uiState.value = ConversationsUiState(isLoading = false, error = error.message)
                        return@addSnapshotListener
                    }
                    val docs = snapshot?.documents ?: emptyList()
                    val list = docs.map { d ->
                        val otherId = d.id
                        val name = d.getString("otherUserName") ?: otherId
                        val lastMessage = d.getString("lastMessage") ?: ""
                        val ts = d.getDate("lastTimestamp") ?: Date(0)
                        val avatar = d.getString("otherUserAvatarUrl")
                        Conversation(id = otherId, otherUserName = name, lastMessage = lastMessage, timestamp = ts, otherUserAvatarUrl = avatar)
                    }.sortedByDescending { it.timestamp }
                    _uiState.value = ConversationsUiState(conversations = list, isLoading = false)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        inboxListener?.remove()
        inboxListener = null
    }
}
