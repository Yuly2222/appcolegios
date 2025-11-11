package com.example.appcolegios.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.appcolegios.perfil.ProfileViewModel

class TasksActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var profileVm: ProfileViewModel

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
        profileVm = ViewModelProvider(this).get(ProfileViewModel::class.java)

        initViews()
        setupRecyclerView()
        setupFilters()

        // Observamos el student seleccionado y recargamos tareas
        lifecycleScope.launch {
            profileVm.student.collectLatest { result ->
                val student = result?.getOrNull()
                val targetId = student?.id ?: auth.currentUser?.uid ?: ""
                loadTasksForTarget(targetId)
            }
        }
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
            // reload current student
            lifecycleScope.launch {
                val student = profileVm.student.value?.getOrNull()
                val targetId = student?.id ?: auth.currentUser?.uid ?: ""
                loadTasksForTarget(targetId)
            }
        }

        chipPending.setOnClickListener {
            currentFilter = "pending"
            lifecycleScope.launch {
                val student = profileVm.student.value?.getOrNull()
                val targetId = student?.id ?: auth.currentUser?.uid ?: ""
                loadTasksForTarget(targetId)
            }
        }

        chipCompleted.setOnClickListener {
            currentFilter = "completed"
            lifecycleScope.launch {
                val student = profileVm.student.value?.getOrNull()
                val targetId = student?.id ?: auth.currentUser?.uid ?: ""
                loadTasksForTarget(targetId)
            }
        }
    }

    private fun loadTasksForTarget(targetId: String) {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        if (targetId.isBlank()) {
            progressBar.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.setText(R.string.no_student_data)
            return
        }

        var query: Query = firestore.collection("tasks")
            .whereEqualTo("studentId", targetId)
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
                    // Intentar resolver los recursos por nombre (evita dependencia en R.string si no se han regenerado los recursos)
                    val pendingId = resources.getIdentifier("no_pending_tasks", "string", packageName)
                    val completedId = resources.getIdentifier("no_completed_tasks", "string", packageName)
                    val availableId = resources.getIdentifier("no_tasks_available", "string", packageName)
                    emptyStateText.text = when (currentFilter) {
                        "pending" -> if (pendingId != 0) getString(pendingId) else "No hay tareas pendientes"
                        "completed" -> if (completedId != 0) getString(completedId) else "No hay tareas completadas"
                        else -> if (availableId != 0) getString(availableId) else "No hay tareas disponibles"
                    }
                } else {
                    // Aquí iría el adaptador con los datos
                    // val tasks = documents.map { it.toObject(Task::class.java) }
                    // tasksAdapter.submitList(tasks)
                    emptyStateText.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(tasksRecyclerView, "Error al cargar tareas: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadTasksForTarget(targetId) }
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
