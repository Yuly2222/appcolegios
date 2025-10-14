package com.example.appcolegios.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TasksActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var chipAll: Chip
    private lateinit var chipPending: Chip
    private lateinit var chipCompleted: Chip

    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        setupFilters()
        loadTasks()
    }

    private fun initViews() {
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
        chipAll = findViewById(R.id.chipAll)
        chipPending = findViewById(R.id.chipPending)
        chipCompleted = findViewById(R.id.chipCompleted)
    }

    private fun setupRecyclerView() {
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilters() {
        chipAll.setOnClickListener {
            currentFilter = "all"
            loadTasks()
        }

        chipPending.setOnClickListener {
            currentFilter = "pending"
            loadTasks()
        }

        chipCompleted.setOnClickListener {
            currentFilter = "completed"
            loadTasks()
        }
    }

    private fun loadTasks() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        var query: Query = firestore.collection("tasks")
            .whereEqualTo("studentId", userId)
            .orderBy("deadline", Query.Direction.ASCENDING)

        // Aplicar filtro
        when (currentFilter) {
            "pending" -> query = query.whereEqualTo("completed", false)
            "completed" -> query = query.whereEqualTo("completed", true)
        }

        query.get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.text = when (currentFilter) {
                        "pending" -> "No hay tareas pendientes"
                        "completed" -> "No hay tareas completadas"
                        else -> "No hay tareas disponibles"
                    }
                } else {
                    // Aquí iría el adaptador con los datos
                    // val tasks = documents.map { it.toObject(Task::class.java) }
                    // tasksAdapter.submitList(tasks)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(tasksRecyclerView, "Error al cargar tareas: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadTasks() }
                    .show()
            }
    }

    fun onTaskChecked(taskId: String, isCompleted: Boolean) {
        firestore.collection("tasks").document(taskId)
            .update("completed", isCompleted)
            .addOnSuccessListener {
                Snackbar.make(tasksRecyclerView, "Tarea actualizada", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Rollback visual
                Snackbar.make(tasksRecyclerView, "Error: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { onTaskChecked(taskId, isCompleted) }
                    .show()
            }
    }
}
