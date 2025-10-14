package com.example.appcolegios.notificaciones

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class NotificationsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        loadNotifications()
    }

    private fun initViews() {
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                } else {
                    // Agrupar por "Hoy" y "Ayer"
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val yesterday = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)

                    // Aquí iría el adaptador con las notificaciones agrupadas
                    // val grouped = documents.groupBy { doc ->
                    //     val date = doc.getTimestamp("timestamp")?.toDate()
                    //     val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date ?: Date())
                    //     when (dateStr) {
                    //         today -> "Hoy"
                    //         yesterdayStr -> "Ayer"
                    //         else -> dateStr
                    //     }
                    // }
                    // notificationsAdapter.submitGroupedData(grouped)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(notificationsRecyclerView, "Error al cargar notificaciones: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadNotifications() }
                    .show()
            }
    }

    fun markAsRead(notificationId: String) {
        firestore.collection("notifications").document(notificationId)
            .update("read", true)
            .addOnFailureListener { e ->
                Snackbar.make(notificationsRecyclerView, "Error al marcar como leída: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
    }
}
