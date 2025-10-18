package com.example.appcolegios.mensajes

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
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
                        attachMessagesListener(chatDocRef, convId)
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
                        .addOnSuccessListener { attachMessagesListener(chatDocRef, convId) }
                        .addOnFailureListener { e ->
                            Snackbar.make(messagesRecyclerView, "No tiene permisos para unirse al chat: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                } else {
                    attachMessagesListener(chatDocRef, convId)
                }
            }
        }.addOnFailureListener { e ->
            Snackbar.make(messagesRecyclerView, "Error comprobando la conversación: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun attachMessagesListener(chatDocRef: com.google.firebase.firestore.DocumentReference, convId: String) {
        messagesListener?.remove()
        messagesListener = chatDocRef.collection("messages")
            .orderBy("fechaHora", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // Manejar permiso denegado (regla Firestore) y otros errores
                    val msg = error.message ?: "Error desconocido al cargar mensajes"
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                        Snackbar.make(messagesRecyclerView, "Permiso denegado: inicia sesión o no tienes acceso a este chat", Snackbar.LENGTH_LONG)
                            .setAction("Iniciar sesión") { startActivity(Intent(this, LoginActivity::class.java)) }
                            .show()
                        finish()
                        return@addSnapshotListener
                    }
                    Snackbar.make(messagesRecyclerView, "Error al cargar mensajes: $msg", Snackbar.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    // Aquí iría el adaptador con los mensajes
                    // val messages = snapshots.map { it.toObject(Message::class.java) }
                    // messagesAdapter.submitList(messages)
                    // messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
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
            "texto" to text,
            "fechaHora" to com.google.firebase.Timestamp.now(),
            "tipo" to "TEXTO",
            "estado" to "ENVIADO"
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
