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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class EventsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var chipToday: Chip
    private lateinit var chipWeek: Chip
    private lateinit var chipMonth: Chip

    private var currentFilter = "today"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupRecyclerView()
        setupFilters()
        loadEvents()
    }

    private fun initViews() {
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
        chipToday = findViewById(R.id.chipToday)
        chipWeek = findViewById(R.id.chipWeek)
        chipMonth = findViewById(R.id.chipMonth)
    }

    private fun setupRecyclerView() {
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilters() {
        chipToday.setOnClickListener {
            currentFilter = "today"
            loadEvents()
        }

        chipWeek.setOnClickListener {
            currentFilter = "week"
            loadEvents()
        }

        chipMonth.setOnClickListener {
            currentFilter = "month"
            loadEvents()
        }
    }

    private fun loadEvents() {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        val calendar = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        var startDate = today
        var endDate = today

        when (currentFilter) {
            "week" -> {
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            }
            "month" -> {
                calendar.add(Calendar.MONTH, 1)
                endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            }
        }

        firestore.collection("events")
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.ASCENDING)
            .orderBy("time", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.text = "No hay eventos en este período"
                } else {
                    // Aquí iría el adaptador con los eventos
                    // val events = documents.map { it.toObject(Event::class.java) }
                    // eventsAdapter.submitList(events)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(eventsRecyclerView, "Error al cargar eventos: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadEvents() }
                    .show()
            }
    }
}
