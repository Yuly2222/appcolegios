package com.example.appcolegios.mensajes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Message
import com.example.appcolegios.data.model.MessageStatus
import com.example.appcolegios.data.model.MessageType
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.*
import com.example.appcolegios.auth.LoginActivity

class ChatActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton

    private var otherUserId: String? = null
    private var messagesListener: ListenerRegistration? = null

    // Adapter de mensajes
    private lateinit var messagesAdapter: MessagesAdapter

    private var listenerUsesTimestamp = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        otherUserId = intent.getStringExtra("conversationId")

        initViews()
        setupRecyclerView()
        setupListeners()
        listenToMessages()
    }

    private fun initViews() {
        messagesRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.chatMessageInput)
        sendButton = findViewById(R.id.chatSendButton)
        attachButton = findViewById(R.id.chatAttachButton)
    }

    private fun setupRecyclerView() {
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messagesAdapter = MessagesAdapter(emptyList())
        messagesRecyclerView.adapter = messagesAdapter
    }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }

        attachButton.setOnClickListener {
            // Implementar selector de archivos y subida a Firebase Storage
            Snackbar.make(it, "Función de adjuntar próximamente", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun conversationIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    private fun listenToMessages() {
        val otherId = otherUserId
        val myId = auth.currentUser?.uid

        if (myId == null) {
            Snackbar.make(messagesRecyclerView, "Debes iniciar sesión para ver mensajes", Snackbar.LENGTH_LONG).setAction("Iniciar sesión") {
                startActivity(Intent(this, LoginActivity::class.java))
            }.show()
            finish()
            return
        }

        if (otherId == null) {
            Snackbar.make(messagesRecyclerView, "Conversación inválida", Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }

        val convId = conversationIdFor(myId, otherId)

        // Asegurar que existe el documento del chat y que el usuario es participante antes de subscribir
        val chatDocRef = firestore.collection("chats").document(convId)
        chatDocRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // Crear documento de chat con ambos participantes
                chatDocRef.set(mapOf("participants" to listOf(myId, otherId), "updatedAt" to com.google.firebase.Timestamp.now()))
                    .addOnSuccessListener {
                        attachMessagesListener(chatDocRef)
                    }
                    .addOnFailureListener { e ->
                        Snackbar.make(messagesRecyclerView, "No se pudo preparar la conversación: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            } else {
                // Comprobar que el usuario es participante (si no lo es, intentar agregarlo de forma segura)
                val participants = (snapshot.data?.get("participants") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (!participants.contains(myId)) {
                    // Añadir de forma conservadora al array (merge)
                    chatDocRef.update("participants", (participants + myId).distinct())
                        .addOnSuccessListener { attachMessagesListener(chatDocRef) }
                        .addOnFailureListener { e ->
                            Snackbar.make(messagesRecyclerView, "No tiene permisos para unirse al chat: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                } else {
                    attachMessagesListener(chatDocRef)
                }
            }
        }.addOnFailureListener { e ->
            Snackbar.make(messagesRecyclerView, "Error comprobando la conversación: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun attachMessagesListener(chatDocRef: com.google.firebase.firestore.DocumentReference) {
        messagesListener?.remove()
        listenerUsesTimestamp = true
        messagesListener = chatDocRef.collection("messages")
            .orderBy(if (listenerUsesTimestamp) "timestamp" else "fechaHora", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // Manejar permiso denegado (regla Firestore) y otros errores
                    val msg = error.message ?: "Error desconocido al cargar mensajes"
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                        val sb = Snackbar.make(messagesRecyclerView, "Permiso denegado: inicia sesión o no tienes acceso a este chat", Snackbar.LENGTH_LONG)
                        sb.setAction("Iniciar sesión") { startActivity(Intent(this@ChatActivity, LoginActivity::class.java)) }
                        sb.show()
                        finish()
                        return@addSnapshotListener
                    }
                    Snackbar.make(messagesRecyclerView, "Error al cargar mensajes: $msg", Snackbar.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Log para debugging: mostrar tamaño y contenido de documentos recibidos
                Log.d("ChatActivity", "[listener ${if (listenerUsesTimestamp) "timestamp" else "fechaHora"}] snapshot received: docs=${snapshots?.documents?.size ?: 0}")
                snapshots?.documents?.forEach { doc ->
                    Log.d("ChatActivity", "doc id=${doc.id} dataKeys=${doc.data?.keys}")
                }

                // Si no hay snapshots o está vacío, actualizar el adaptador con lista vacía
                if (snapshots == null || snapshots.isEmpty) {
                    messagesAdapter.update(emptyList())
                    return@addSnapshotListener
                }

                // Si usamos timestamp pero los docs no contienen el campo `timestamp`, reatachar por fechaHora
                if (listenerUsesTimestamp) {
                    val anyHasTimestamp = snapshots.documents.any { it.getTimestamp("timestamp") != null }
                    if (!anyHasTimestamp) {
                        Log.i("ChatActivity", "No hay campo 'timestamp' en los documentos, reattach listener using 'fechaHora'")
                        messagesListener?.remove()
                        listenerUsesTimestamp = false
                        messagesListener = chatDocRef.collection("messages")
                            .orderBy("fechaHora", Query.Direction.ASCENDING)
                            .addSnapshotListener { snapshots2, error2 ->
                                // reutilizar la misma lógica de mapeo (ignorar recursividad)
                                if (error2 != null) {
                                    Log.w("ChatActivity", "listener(fechaHora) error: ${error2.message}")
                                    return@addSnapshotListener
                                }
                                if (snapshots2 == null || snapshots2.isEmpty) {
                                    messagesAdapter.update(emptyList())
                                    return@addSnapshotListener
                                }
                                val messages = snapshots2.documents.mapNotNull { doc ->
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
                                        Log.w("ChatActivity", "error mapping doc ${doc.id}: ${e.message}")
                                        null
                                    }
                                }
                                Log.d("ChatActivity", "mapped messages count=${messages.size} (fechaHora listener)")
                                messagesAdapter.update(messages)
                                if (messages.isNotEmpty()) messagesRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        return@addSnapshotListener
                    }
                }

                // Mapear documentos a Message y actualizar el adaptador
                val messages = snapshots.documents.mapNotNull { doc ->
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
                        Log.w("ChatActivity", "error mapping doc ${doc.id}: ${e.message}")
                        null
                    }
                }
                Log.d("ChatActivity", "mapped messages count=${messages.size} (listener ${if (listenerUsesTimestamp) "timestamp" else "fechaHora"})")
                messagesAdapter.update(messages)
                if (messages.isNotEmpty()) messagesRecyclerView.scrollToPosition(messages.size - 1)
            }
        // Marcar la conversación como leída en el inbox del usuario actual (si otherUserId está presente)
        try {
            otherUserId?.let { oid ->
                markConversationAsRead(oid)
            }
        } catch (_: Exception) { }
    }

    // Establece unreadCount = 0 en users/{myId}/inbox/{otherId}
    private fun markConversationAsRead(otherId: String) {
        val myId = auth.currentUser?.uid ?: return
        try {
            val inboxRef = firestore.collection("users").document(myId).collection("inbox").document(otherId)
            // Usar merge para no borrar otros campos
            inboxRef.set(mapOf("unreadCount" to 0), com.google.firebase.firestore.SetOptions.merge())
        } catch (_: Exception) { }
    }

    private fun sendMessage(text: String) {
        val myId = auth.currentUser?.uid ?: return
        val otherId = otherUserId ?: return
        val convId = conversationIdFor(myId, otherId)
        sendButton.isEnabled = false

        val messageData = mapOf(
            "id" to UUID.randomUUID().toString(),
            "fromId" to myId,
            "toId" to otherId,
            // Escribir en ambos esquemas para compatibilidad
            "texto" to text,
            "text" to text,
            "fechaHora" to com.google.firebase.Timestamp.now(),
            "timestamp" to com.google.firebase.Timestamp.now(),
            "tipo" to "TEXTO",
            "type" to "TEXTO",
            "estado" to "ENVIADO",
            "status" to "ENVIADO"
        )

        // Ensure chat meta exists
        firestore.collection("chats").document(convId)
            .set(mapOf("participants" to listOf(myId, otherId), "updatedAt" to com.google.firebase.Timestamp.now()), com.google.firebase.firestore.SetOptions.merge())
            .addOnCompleteListener {
                // Add message
                firestore.collection("chats").document(convId)
                    .collection("messages")
                    .add(messageData)
                    .addOnSuccessListener {
                        messageInput.text.clear()
                        sendButton.isEnabled = true
                        // update meta
                        firestore.collection("chats").document(convId)
                            .update(mapOf("updatedAt" to com.google.firebase.Timestamp.now()))
                    }
                    .addOnFailureListener { e ->
                        sendButton.isEnabled = true
                        Snackbar.make(messagesRecyclerView, "Error al enviar: ${e.message}", Snackbar.LENGTH_LONG)
                            .setAction("Reintentar") { sendMessage(text) }
                            .show()
                    }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
    }
}
