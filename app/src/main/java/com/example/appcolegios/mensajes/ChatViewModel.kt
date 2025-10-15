package com.example.appcolegios.mensajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcolegios.data.model.Message
import com.example.appcolegios.data.model.MessageStatus
import com.example.appcolegios.data.model.MessageType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val otherUserName: String? = null,
    val otherUserAvatarUrl: String? = null
)

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var messagesListener: ListenerRegistration? = null
    private var currentOtherUserId: String? = null

    private fun conversationIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun loadMessagesWith(otherUserId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = ChatUiState(isLoading = false, error = "Usuario no autenticado.")
                return@launch
            }

            if (otherUserId.isBlank() || otherUserId.equals("unknown", ignoreCase = true)) {
                _uiState.value = ChatUiState(messages = emptyList(), isLoading = false, error = null)
                return@launch
            }

            if (currentOtherUserId == otherUserId && messagesListener != null) {
                return@launch
            }

            // Cambiar listener a la nueva conversación
            messagesListener?.remove()
            messagesListener = null
            currentOtherUserId = otherUserId
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Cargar perfil del otro usuario (publicProfiles o users)
            try {
                val (name, avatar) = fetchUserProfile(otherUserId)
                _uiState.value = _uiState.value.copy(otherUserName = name, otherUserAvatarUrl = avatar)
            } catch (_: Exception) { /* nombre/imagen opcional */ }

            val convId = conversationIdFor(userId, otherUserId)
            try {
                // Comprobar/crear doc meta del chat (asegura participants y evita PERMISSION_DENIED)
                val chatDocRef = db.collection("chats").document(convId)
                val chatSnap = try { chatDocRef.get().await() } catch (_: Exception) { null }
                if (chatSnap == null || !chatSnap.exists()) {
                    try {
                        chatDocRef.set(mapOf("participants" to listOf(userId, otherUserId), "updatedAt" to com.google.firebase.Timestamp.now()))
                    } catch (e: Exception) {
                        _uiState.value = ChatUiState(isLoading = false, error = "No se pudo preparar chat: ${e.message}", otherUserName = _uiState.value.otherUserName, otherUserAvatarUrl = _uiState.value.otherUserAvatarUrl)
                        return@launch
                    }
                } else {
                    // si no contiene al usuario, intentar agregarlo de forma conservadora
                    val participants = (chatSnap.data?.get("participants") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    if (!participants.contains(userId)) {
                        try {
                            chatDocRef.update("participants", (participants + userId).distinct())
                        } catch (_: Exception) { /* si falla, igual intentamos attach listener y Firestore reglas mandarán permiso si aplica */ }
                    }
                }

                // Ahora podemos atachar el listener de forma segura
                val conversationRef = db.collection("chats").document(convId).collection("messages")
                messagesListener = conversationRef
                    .orderBy("fechaHora", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            _uiState.value = ChatUiState(isLoading = false, error = error.message, otherUserName = _uiState.value.otherUserName, otherUserAvatarUrl = _uiState.value.otherUserAvatarUrl)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val messages = snapshot.toObjects(Message::class.java)
                            _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                        } else {
                            _uiState.value = _uiState.value.copy(messages = emptyList(), isLoading = false)
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState(isLoading = false, error = e.message, otherUserName = _uiState.value.otherUserName, otherUserAvatarUrl = _uiState.value.otherUserAvatarUrl)
            }

            // Marcar como leída la conversación para el usuario actual
            resetUnreadCount(userId, otherUserId)
        }
    }

    private suspend fun fetchUserProfile(uid: String): Pair<String, String?> {
        // Intenta publicProfiles primero
        val pubDoc = try { db.collection("publicProfiles").document(uid).get().await() } catch (_: Exception) { null }
        if (pubDoc != null && pubDoc.exists()) {
            val name = pubDoc.getString("name") ?: pubDoc.getString("displayName") ?: pubDoc.getString("email") ?: uid
            val avatar = pubDoc.getString("avatarUrl")
            return name to avatar
        }
        // Fallback a users
        val userDoc = db.collection("users").document(uid).get().await()
        val name = userDoc.getString("name") ?: userDoc.getString("displayName") ?: userDoc.getString("email") ?: uid
        val avatar = userDoc.getString("avatarUrl")
        return name to avatar
    }

    fun sendMessage(text: String, otherUserId: String) {
        viewModelScope.launch {
            val fromId = auth.currentUser?.uid ?: return@launch
            val convId = conversationIdFor(fromId, otherUserId)
            val now = Date()

            // Asegurar doc meta del chat con participantes
            try {
                db.collection("chats").document(convId)
                    .set(
                        mapOf(
                            "participants" to listOf(fromId, otherUserId),
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (_: Exception) { }

            val message = Message(
                id = UUID.randomUUID().toString(),
                fromId = fromId,
                toId = otherUserId,
                texto = text,
                fechaHora = now,
                tipo = MessageType.TEXTO,
                estado = MessageStatus.ENVIADO
            )
            try {
                db.collection("chats").document(convId)
                    .collection("messages").add(message).await()
                // Actualizar inbox para ambos usuarios
                updateInboxOnSend(fromId, otherUserId, text)
            } catch (_: Exception) {
                // log opcional
            }
        }
    }

    private suspend fun updateInboxOnSend(fromId: String, toId: String, lastMessage: String) {
        val now = Date()
        val usersColl = db.collection("users")
        // Intentar obtener nombres (fallback a IDs)
        val fromName = try { usersColl.document(fromId).get().await().getString("name") ?: fromId } catch (_: Exception) { fromId }
        val toName = try { usersColl.document(toId).get().await().getString("name") ?: toId } catch (_: Exception) { toId }

        // Para el emisor: inbox/{toId}
        val fromInboxRef = usersColl.document(fromId).collection("inbox").document(toId)
        fromInboxRef.set(
             mapOf(
                 "otherUserName" to toName,
                 "otherUserAvatarUrl" to null,
                 "lastMessage" to lastMessage,
                 "lastTimestamp" to now,
                 "unreadCount" to 0
             ),
            SetOptions.merge()
         ).await()

         // Para el receptor: inbox/{fromId}, incrementar unreadCount
         val toInboxRef = usersColl.document(toId).collection("inbox").document(fromId)
         toInboxRef.set(
             mapOf(
                 "otherUserName" to fromName,
                 "otherUserAvatarUrl" to null,
                 "lastMessage" to lastMessage,
                 "lastTimestamp" to now
             ),
            SetOptions.merge()
         ).await()
         toInboxRef.update("unreadCount", FieldValue.increment(1)).await()
    }

    private fun resetUnreadCount(userId: String, otherUserId: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("inbox").document(otherUserId)
                    .update("unreadCount", 0).await()
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        messagesListener = null
    }
}
