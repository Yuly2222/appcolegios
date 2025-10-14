package com.example.appcolegios.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appcolegios.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var attendanceRecyclerView: RecyclerView
    private lateinit var attendancePercentageText: TextView
    private lateinit var currentMonthText: TextView
    private lateinit var previousMonthButton: MaterialButton
    private lateinit var nextMonthButton: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var currentMonth = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        setupRecyclerView()
        setupListeners()
        loadAttendance()
    }

    private fun initViews() {
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView)
        attendancePercentageText = findViewById(R.id.attendancePercentageText)
        currentMonthText = findViewById(R.id.currentMonthText)
        previousMonthButton = findViewById(R.id.previousMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)
        progressBar = findViewById(R.id.progressBar)

        updateMonthText()
    }

    private fun loadAttendance() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        // Calcular rango del mes
        val startDate = Calendar.getInstance().apply {
            time = currentMonth.time
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val endDate = Calendar.getInstance().apply {
            time = currentMonth.time
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startDate.time)
        val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endDate.time)

        firestore.collection("attendance")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startDateStr)
            .whereLessThanOrEqualTo("date", endDateStr)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                // Calcular porcentaje
                val totalDays = endDate.getActualMaximum(Calendar.DAY_OF_MONTH)
                val presentDays = documents.count { it.getString("status") == "present" }
                val percentage = (presentDays * 100) / totalDays

                attendancePercentageText.text = getString(R.string.attendance_percentage, percentage)

                // Aquí iría el adaptador del calendario con los días marcados
                // val attendanceMap = documents.associate { it.getString("date")!! to it.getString("status")!! }
                // attendanceAdapter.submitData(attendanceMap)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Snackbar.make(attendanceRecyclerView, "Error al cargar asistencia: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { loadAttendance() }
                    .show()
            }
    }

    private fun setupRecyclerView() {
        attendanceRecyclerView.layoutManager = GridLayoutManager(this, 7)
    }

    private fun setupListeners() {
        previousMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            updateMonthText()
            loadAttendance()
        }

        nextMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            updateMonthText()
            loadAttendance()
        }
    }

    private fun updateMonthText() {
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthText = format.format(currentMonth.time).replaceFirstChar { char ->
            char.titlecase(Locale.getDefault())
        }
        currentMonthText.text = monthText
    }
}
