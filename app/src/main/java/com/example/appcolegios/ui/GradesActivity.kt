package com.example.appcolegios.ui

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
import java.util.*

class GradesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var gradesRecyclerView: RecyclerView
    private lateinit var averageText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grades)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        loadGrades()
    }

    private fun initViews() {
        gradesRecyclerView = findViewById(R.id.gradesRecyclerView)
        averageText = findViewById(R.id.averageText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        gradesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadGrades() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("grades")
            .whereEqualTo("studentId", userId)
            .orderBy("materia", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (!documents.isEmpty) {
                    // Calcular promedio global
                    val grades = documents.mapNotNull { it.getDouble("calificacion") }
                    val average = if (grades.isNotEmpty()) grades.average() else 0.0
                    averageText.text = String.format(Locale.getDefault(), "%.2f", average)

                    // Aquí iría el adaptador con las calificaciones
                    // val gradesList = documents.map { it.toObject(Grade::class.java) }
                    // gradesAdapter.submitList(gradesList)
                } else {
                    averageText.text = "N/A"
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(gradesRecyclerView, "Error al cargar notas: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadGrades() }
                    .show()
            }
    }
}
