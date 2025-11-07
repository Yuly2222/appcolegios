package com.example.appcolegios.mensajes

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Activity for course chat. Shows messages in a RecyclerView and allows sending.
 */
class CourseChatActivity : AppCompatActivity() {
    private val vm: ChatViewModel by viewModels()
    private lateinit var auth: FirebaseAuth

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton

    private var courseId: String? = null
    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_chat)

        auth = FirebaseAuth.getInstance()

        courseId = intent.getStringExtra("courseId")

        messagesRecyclerView = findViewById(R.id.courseChatRecyclerView)
        messageInput = findViewById(R.id.courseChatMessageInput)
        sendButton = findViewById(R.id.courseChatSendButton)

        adapter = MessagesAdapter(emptyList())
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        if (courseId.isNullOrBlank()) {
            Snackbar.make(messagesRecyclerView, "Curso inválido", Snackbar.LENGTH_LONG).show()
            finish()
            return
        }

        vm.listenCourseChat(courseId!!, onChange = { msgs ->
            // update adapter on main thread
            lifecycleScope.launch {
                adapter.update(msgs)
                messagesRecyclerView.scrollToPosition(maxOf(0, msgs.size - 1))
            }
        }, onError = { err ->
            Snackbar.make(messagesRecyclerView, "Error chat curso: $err", Snackbar.LENGTH_LONG).show()
        })

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                vm.sendCourseMessage(courseId!!, text)
                messageInput.text.clear()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel clears listeners in onCleared
    }
}
