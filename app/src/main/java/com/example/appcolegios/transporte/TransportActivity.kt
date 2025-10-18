package com.example.appcolegios.transporte

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.button.MaterialButton

class TransportActivity : AppCompatActivity() {

    private lateinit var transportModeGroup: RadioGroup
    private lateinit var registerButton: MaterialButton
    private lateinit var lastAttendanceText: TextView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var alreadyRegisteredToday = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transport)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        checkTodayAttendance()
        setupListeners()
    }

    private fun initViews() {
        transportModeGroup = findViewById(R.id.transportModeGroup)
        registerButton = findViewById(R.id.registerAttendanceButton)
        lastAttendanceText = findViewById(R.id.lastAttendanceText)
    }

    private fun checkTodayAttendance() {
        val userId = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        firestore.collection("attendance_transport")
            .whereEqualTo("userId", userId)
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { documents ->
                alreadyRegisteredToday = !documents.isEmpty
                registerButton.isEnabled = !alreadyRegisteredToday

                if (alreadyRegisteredToday) {
                    val doc = documents.documents[0]
                    val mode = doc.getString("transportMode") ?: "Desconocido"
                    val time = doc.getTimestamp("timestamp")?.toDate()
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(time ?: Date())
                    lastAttendanceText.text = getString(R.string.last_attendance_today, timeStr, mode)
                    registerButton.text = getString(R.string.already_registered_today)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.check_attendance_error_prefix, e.message ?: "-"),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setupListeners() {
        registerButton.setOnClickListener {
            if (alreadyRegisteredToday) {
                Snackbar.make(it, getString(R.string.already_registered_today_snackbar), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedId = transportModeGroup.checkedRadioButtonId
            if (selectedId == -1) {
                Snackbar.make(it, getString(R.string.select_transport_mode_prompt), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val transportMode = when (selectedId) {
                R.id.radioWalking -> "A pie"
                R.id.radioBicycle -> "Bicicleta"
                R.id.radioPublic -> "Transporte público"
                R.id.radioFamily -> "Familiar"
                else -> "Desconocido"
            }

            registerAttendance(transportMode)
        }
    }

    private fun registerAttendance(transportMode: String) {
        val userId = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        registerButton.isEnabled = false

        val attendanceData = hashMapOf(
            "userId" to userId,
            "date" to today,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "transportMode" to transportMode
        )

        firestore.collection("attendance_transport")
            .add(attendanceData)
            .addOnSuccessListener {
                // Actualizar asistencia del día
                updateDailyAttendance(userId, today)

                alreadyRegisteredToday = true
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                lastAttendanceText.text = getString(R.string.last_attendance_today, timeStr, transportMode)
                registerButton.text = getString(R.string.already_registered_today)

                Snackbar.make(registerButton, getString(R.string.attendance_registered_success), Snackbar.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                registerButton.isEnabled = true
                Snackbar.make(
                    registerButton,
                    getString(R.string.register_error_prefix, e.message ?: "-"),
                    Snackbar.LENGTH_LONG
                )
                    .setAction(getString(R.string.retry)) { registerAttendance(transportMode) }
                    .show()
            }
    }

    private fun updateDailyAttendance(userId: String, date: String) {
        val attendanceRef = firestore.collection("attendance").document("${userId}_$date")
        attendanceRef.set(hashMapOf(
            "userId" to userId,
            "date" to date,
            "status" to "present",
            "timestamp" to com.google.firebase.Timestamp.now()
        ), com.google.firebase.firestore.SetOptions.merge())
    }
}
