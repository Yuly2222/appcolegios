package com.example.appcolegios.mensajes

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ConversationsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabNewChat: FloatingActionButton

    private var inboxListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        setupListeners()
        loadConversations()
    }

    private fun initViews() {
        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
        fabNewChat = findViewById(R.id.fabNewChat)
    }

    private fun setupRecyclerView() {
        conversationsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        fabNewChat.setOnClickListener { view ->
            // Dialog simple para pedir el otherUserId (en una app real deberías mostrar la lista de contactos)
            val input = EditText(this)
             input.hint = "ID usuario o 'course:<courseId>' para chat por curso"
            AlertDialog.Builder(this)
                .setTitle("Crear conversación")
                .setMessage("Ingresa el ID del usuario o 'course:<courseId>' para iniciar chat por curso")
                .setView(input)
                .setPositiveButton("Crear") { dialog, _ ->
                    val raw = input.text.toString().trim()
                    if (raw.isNotEmpty()) {
                        if (raw.startsWith("course:", ignoreCase = true)) {
                            val cid = raw.substringAfter(":").trim()
                            if (cid.isNotEmpty()) {
                                // abrir CourseChatActivity
                                startActivity(android.content.Intent(this, CourseChatActivity::class.java).apply { putExtra("courseId", cid) })
                            }
                        } else {
                            createConversationWith(raw, view)
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                .show()
        }
    }

    private fun loadConversations() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        inboxListener?.remove()
        inboxListener = firestore.collection("users").document(userId)
            .collection("inbox")
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    Snackbar.make(conversationsRecyclerView, "Error al cargar conversaciones: ${error.message}", Snackbar.LENGTH_LONG)
                        .setAction("Reintentar") { loadConversations() }
                        .show()
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                } else {
                    emptyStateText.visibility = View.GONE
                    // Aquí iría el adaptador con las conversaciones
                    // Convertir docs en Conversation y enviar al adaptador
                }
            }
    }

    private fun createConversationWith(otherId: String, anchorView: View) {
        val myId = auth.currentUser?.uid
        if (myId == null) {
            Snackbar.make(anchorView, "Debes iniciar sesión para crear conversaciones", Snackbar.LENGTH_LONG).show()
            return
        }

        if (otherId == myId) {
            Snackbar.make(anchorView, "No puedes crear una conversación contigo mismo", Snackbar.LENGTH_SHORT).show()
            return
        }

        val convId = listOf(myId, otherId).sorted().joinToString("_")
        val now = com.google.firebase.Timestamp.now()

        // Crear metadata de chat (si no existe) y actualizar los inbox de ambos
        val chatRef = firestore.collection("chats").document(convId)
        chatRef.set(mapOf("participants" to listOf(myId, otherId), "updatedAt" to now), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                // actualizar inbox del emisor
                val usersColl = firestore.collection("users")
                val myInboxRef = usersColl.document(myId).collection("inbox").document(otherId)
                myInboxRef.set(mapOf("otherUserName" to otherId, "otherUserAvatarUrl" to null, "lastMessage" to "", "lastTimestamp" to now, "unreadCount" to 0), com.google.firebase.firestore.SetOptions.merge())
                // actualizar inbox receptor
                val toInboxRef = usersColl.document(otherId).collection("inbox").document(myId)
                toInboxRef.set(mapOf("otherUserName" to myId, "otherUserAvatarUrl" to null, "lastMessage" to "", "lastTimestamp" to now), com.google.firebase.firestore.SetOptions.merge())

                // Abrir ChatActivity con otherUserId
                startActivity(android.content.Intent(this, ChatActivity::class.java).apply { putExtra("conversationId", otherId) })
            }
            .addOnFailureListener { e ->
                Snackbar.make(anchorView, "Error creando conversación: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        inboxListener?.remove()
    }
}
