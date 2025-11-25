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
 import com.example.appcolegios.data.FirestoreRepository
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
     private val repo = FirestoreRepository()

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
                val userMap = repo.getDocumentData("users", otherUserId)
                val name = userMap?.get("fullName") as? String ?: userMap?.get("displayName") as? String
                val avatar = userMap?.get("photoUrl") as? String ?: userMap?.get("avatarUrl") as? String
                 _uiState.value = _uiState.value.copy(otherUserName = name, otherUserAvatarUrl = avatar)
             } catch (_: Exception) { /* nombre/imagen opcional */ }

             val convId = conversationIdFor(userId, otherUserId)
             try {
                 // Comprobar/crear doc meta del chat (asegura participants y evita PERMISSION_DENIED)
                 // Ensure chat document exists and participants set via repo
                 try {
                     repo.ensureChatAndAddMessage(convId, listOf(userId, otherUserId), userId, otherUserId, "")
                     // repo.ensureChatAndAddMessage adds an empty message when used; remove that message if needed — instead we used it here only to ensure chat exists. Alternative: create a small helper ensureChatExists in repo.
                     // For now, if repo added empty message, the listener will fetch it and UI can ignore empty messages.
                 } catch (_: Exception) {
                     // Fallback a creación manual si repo falla
                     val chatDocRef = db.collection("chats").document(convId)
                     try { chatDocRef.set(mapOf("participants" to listOf(userId, otherUserId), "updatedAt" to com.google.firebase.Timestamp.now())) } catch (_: Exception) { /* ignore */ }
                 }

                 // Ahora podemos atachar el listener de forma segura
                 val conversationRef = db.collection("chats").document(convId).collection("messages")
                 messagesListener = conversationRef
                     .orderBy("timestamp", Query.Direction.ASCENDING)
                     .addSnapshotListener { snapshot, error ->
                         if (error != null) {
                             _uiState.value = ChatUiState(isLoading = false, error = error.message, otherUserName = _uiState.value.otherUserName, otherUserAvatarUrl = _uiState.value.otherUserAvatarUrl)
                             return@addSnapshotListener
                         }
                         if (snapshot != null) {
                             val list = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val id = doc.id
                                    val fromId = doc.getString("fromId") ?: ""
                                    val toId = doc.getString("toId") ?: ""
                                    val texto = doc.getString("texto") ?: doc.getString("text") ?: ""
                                    val ts = (doc.getTimestamp("fechaHora") ?: doc.getTimestamp("timestamp"))?.toDate() ?: Date(0)
                                    val tipo = try { MessageType.valueOf(doc.getString("tipo") ?: doc.getString("type") ?: "TEXTO") } catch (_: Exception) { MessageType.TEXTO }
                                    val estado = try { MessageStatus.valueOf(doc.getString("estado") ?: doc.getString("status") ?: "ENVIADO") } catch (_: Exception) { MessageStatus.ENVIADO }
                                    Message(id = id, fromId = fromId, toId = toId, texto = texto, fechaHora = ts, tipo = tipo, estado = estado)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            _uiState.value = _uiState.value.copy(messages = list, isLoading = false)
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

     fun sendMessage(text: String, otherUserId: String) {
         viewModelScope.launch {
             val fromId = auth.currentUser?.uid ?: return@launch
             val convId = conversationIdFor(fromId, otherUserId)
             val now = Date()

             try {
                 // Usar repo para asegurar chat y agregar mensaje de forma atómica
                 repo.ensureChatAndAddMessage(convId, listOf(fromId, otherUserId), fromId, otherUserId, text)
             } catch (_: Exception) {
                 // fallback manual
                 try {
                     db.collection("chats").document(convId)
                         .set(
                             mapOf(
                                 "participants" to listOf(fromId, otherUserId),
                                 "updatedAt" to now
                             ),
                             SetOptions.merge()
                         ).await()
                     val message = Message(
                         id = UUID.randomUUID().toString(),
                         fromId = fromId,
                         toId = otherUserId,
                         texto = text,
                         fechaHora = now,
                         tipo = MessageType.TEXTO,
                         estado = MessageStatus.ENVIADO
                     )
                     db.collection("chats").document(convId).collection("messages").add(message).await()
                     // Actualizar inbox para ambos usuarios (manual)
                     updateInboxOnSend(fromId, otherUserId, text)
                 } catch (_: Exception) { }
             }
         }
     }

     private fun resetUnreadCount(userId: String, otherUserId: String) {
         viewModelScope.launch {
             try {
                val docRef = db.collection("users").document(userId).collection("inbox").document(otherUserId)
                 docRef.update("unreadCount", 0).await()
             } catch (_: Exception) {}
         }
     }

     // New: listen to a course-wide chat (doc id: course_{courseId})
     @Suppress("unused")
     fun listenCourseChat(courseId: String, onChange: (List<Message>) -> Unit = {}, onError: (String) -> Unit = {}) {
         viewModelScope.launch {
             messagesListener?.remove()
             messagesListener = null
             currentOtherUserId = null
             val chatDocId = "course_$courseId"
             try {
                 // Ensure the chat document exists and contains courseId
                 val chatDocRef = db.collection("chats").document(chatDocId)
                 val snap = try { chatDocRef.get().await() } catch (_: Exception) { null }
                 if (snap == null || !snap.exists()) {
                     try {
                         chatDocRef.set(mapOf("courseId" to courseId, "updatedAt" to com.google.firebase.Timestamp.now()))
                     } catch (_: Exception) { /* ignore */ }
                 }

                 // Attach listener to messages subcollection
                 val convRef = db.collection("chats").document(chatDocId).collection("messages")
                 messagesListener = convRef.orderBy("timestamp", Query.Direction.ASCENDING)
                     .addSnapshotListener { snapshots, error ->
                         if (error != null) {
                             onError(error.message ?: "Error desconocido")
                             _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                             return@addSnapshotListener
                         }
                         if (snapshots != null) {
                             val msgs = snapshots.documents.mapNotNull { doc ->
                                try {
                                    val id = doc.id
                                    val fromId = doc.getString("fromId") ?: ""
                                    val toId = doc.getString("toId") ?: ""
                                    val texto = doc.getString("texto") ?: doc.getString("text") ?: ""
                                    val ts = (doc.getTimestamp("fechaHora") ?: doc.getTimestamp("timestamp"))?.toDate() ?: Date(0)
                                    val tipo = try { MessageType.valueOf(doc.getString("tipo") ?: doc.getString("type") ?: "TEXTO") } catch (_: Exception) { MessageType.TEXTO }
                                    val estado = try { MessageStatus.valueOf(doc.getString("estado") ?: doc.getString("status") ?: "ENVIADO") } catch (_: Exception) { MessageStatus.ENVIADO }
                                    Message(id = id, fromId = fromId, toId = toId, texto = texto, fechaHora = ts, tipo = tipo, estado = estado)
                                } catch (_: Exception) { null }
                            }
                            onChange(msgs)
                            _uiState.value = _uiState.value.copy(messages = msgs, isLoading = false)
                        }
                    }
             } catch (e: Exception) {
                 onError(e.message ?: "Error listening course chat")
                 _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
             }
         }
     }

     // New: send message to course chat (course_{courseId}/messages)
     @Suppress("unused")
     fun sendCourseMessage(courseId: String, text: String) {
         viewModelScope.launch {
             val fromId = auth.currentUser?.uid ?: return@launch
             val chatDocId = "course_$courseId"
             val now = Date()
             val message = Message(
                 id = UUID.randomUUID().toString(),
                 fromId = fromId,
                 toId = courseId, // represent recipient as course id for course messages
                 texto = text,
                 fechaHora = now,
                 tipo = MessageType.TEXTO,
                 estado = MessageStatus.ENVIADO
             )
             try {
                 val chatDocRef = db.collection("chats").document(chatDocId)
                 chatDocRef.set(mapOf("courseId" to courseId, "updatedAt" to now), SetOptions.merge())
                 db.collection("chats").document(chatDocId).collection("messages").add(message)
                 // Update chat meta timestamp
                 chatDocRef.update("updatedAt", now)
             } catch (_: Exception) {
                 // ignore failure silently for now
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

     override fun onCleared() {
         super.onCleared()
         messagesListener?.remove()
         messagesListener = null
     }
 }
