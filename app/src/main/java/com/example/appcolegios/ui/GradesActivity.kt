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
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import com.example.appcolegios.perfil.ProfileViewModel

class GradesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var profileVm: ProfileViewModel

    private lateinit var gradesRecyclerView: RecyclerView
    private lateinit var averageText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grades)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        profileVm = ViewModelProvider(this).get(ProfileViewModel::class.java)

        initViews()
        setupRecyclerView()

        // Observamos el student seleccionado desde ProfileViewModel; cuando cambie recargamos notas
        lifecycleScope.launch {
            profileVm.student.collectLatest { result ->
                val student = result?.getOrNull()
                val targetId = student?.id ?: auth.currentUser?.uid ?: ""
                val studentName = student?.nombre?.takeIf { it.isNotBlank() }
                loadGradesForTarget(targetId, studentName)
            }
        }
    }

    private fun initViews() {
        gradesRecyclerView = findViewById(R.id.gradesRecyclerView)
        averageText = findViewById(R.id.averageText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        gradesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadGradesForTarget(targetId: String, studentName: String?) {
        if (targetId.isBlank()) {
            progressBar.visibility = View.GONE
            Snackbar.make(gradesRecyclerView, "Usuario no identificado", Snackbar.LENGTH_LONG).show()
            return
        }

        // Opcional: mostrar nombre en la Toolbar si se conoce
        if (!studentName.isNullOrBlank()) {
            supportActionBar?.title = "Notas: $studentName"
        }

        progressBar.visibility = View.VISIBLE
        firestore.collection("grades")
            .whereEqualTo("studentId", targetId)
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
                } else {
                    averageText.text = "N/A"
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(gradesRecyclerView, "Error al cargar notas: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadGradesForTarget(targetId, studentName) }
                    .show()
            }
    }
}
