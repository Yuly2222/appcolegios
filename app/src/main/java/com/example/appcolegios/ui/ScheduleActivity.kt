package com.example.appcolegios.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
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

class ScheduleActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var scheduleRecyclerView: RecyclerView
    private lateinit var currentDayText: TextView
    private lateinit var previousDayButton: Button
    private lateinit var nextDayButton: Button
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar

    private var currentDay = Calendar.getInstance()
    private val daysOfWeek = arrayOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        setupListeners()
        loadSchedule()
    }

    private fun initViews() {
        scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView)
        currentDayText = findViewById(R.id.currentDayText)
        previousDayButton = findViewById(R.id.previousDayButton)
        nextDayButton = findViewById(R.id.nextDayButton)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)

        updateDayText()
    }

    private fun setupRecyclerView() {
        scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        previousDayButton.setOnClickListener {
            currentDay.add(Calendar.DAY_OF_WEEK, -1)
            updateDayText()
            loadSchedule()
        }

        nextDayButton.setOnClickListener {
            currentDay.add(Calendar.DAY_OF_WEEK, 1)
            updateDayText()
            loadSchedule()
        }
    }

    private fun updateDayText() {
        val dayOfWeek = currentDay.get(Calendar.DAY_OF_WEEK)
        currentDayText.text = daysOfWeek[dayOfWeek - 1]
    }

    private fun loadSchedule() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        val dayOfWeek = daysOfWeek[currentDay.get(Calendar.DAY_OF_WEEK) - 1]

        firestore.collection("schedule")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("dayOfWeek", dayOfWeek)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                } else {
                    // Aquí iría el adaptador con los bloques horarios
                    // val classes = documents.map { it.toObject(ClassBlock::class.java) }
                    // scheduleAdapter.submitList(classes)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(scheduleRecyclerView, "Error al cargar horario: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadSchedule() }
                    .show()
            }
    }
}
