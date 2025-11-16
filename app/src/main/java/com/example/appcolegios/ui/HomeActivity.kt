package com.example.appcolegios.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.example.appcolegios.notificaciones.NotificationsActivity
import com.example.appcolegios.mensajes.ConversationsActivity
import com.example.appcolegios.pagos.PaymentsActivity
import com.example.appcolegios.transporte.TransportActivity
import com.example.appcolegios.perfil.AcademicInfoActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Cargar acciones dinámicas desde Firestore y poblar el contenedor `homeActionsContainer`.
        val container = findViewById<android.widget.LinearLayout>(R.id.homeActionsContainer)
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        fun createActionButton(label: String, intent: Intent) {
            val btn = Button(this)
            btn.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            btn.text = label
            btn.setOnClickListener { startActivity(intent) }
            val margin = resources.displayMetrics.density.times(8).toInt()
            val lp = android.widget.LinearLayout.LayoutParams(btn.layoutParams).apply { topMargin = margin }
            btn.layoutParams = lp
            container.addView(btn)
        }

        fun fallbackActions() {
            // Notas
            createActionButton("Notas", Intent(this, GradesActivity::class.java))
            // Asistencia
            createActionButton("Asistencia", Intent(this, AttendanceActivity::class.java))
            // Tareas
            createActionButton("Tareas", Intent(this, TasksActivity::class.java))
        }

        // Cargar rol del usuario si está autenticado
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // sin sesión -> fallback
            fallbackActions()
        } else {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val role = doc.getString("role") ?: doc.getString("roleEnum") ?: "student"
                    // consultar acciones para este rol
                    db.collection("home_actions").whereEqualTo("role", role).get()
                        .addOnSuccessListener { snap ->
                            if (snap == null || snap.isEmpty) {
                                fallbackActions()
                            } else {
                                for (actionDoc in snap.documents) {
                                    val label = actionDoc.getString("label") ?: continue
                                    val route = actionDoc.getString("route") ?: continue
                                    // mapear rutas conocidas a Activities
                                    val intent = when (route.lowercase()) {
                                        "grades", "notas" -> Intent(this, GradesActivity::class.java)
                                        "attendance", "asistencia" -> Intent(this, AttendanceActivity::class.java)
                                        "tasks", "tareas" -> Intent(this, TasksActivity::class.java)
                                        "calendar" -> Intent(this, CalendarActivity::class.java)
                                        "messages", "mensajes" -> Intent(this, ConversationsActivity::class.java)
                                        "payments" -> Intent(this, PaymentsActivity::class.java)
                                        "transport" -> Intent(this, TransportActivity::class.java)
                                        "profile" -> Intent(this, AcademicInfoActivity::class.java)
                                        else -> null
                                    }
                                    if (intent != null) createActionButton(label, intent)
                                }
                            }
                        }
                        .addOnFailureListener {
                            fallbackActions()
                        }
                }
                .addOnFailureListener {
                    fallbackActions()
                }
        }

        findViewById<Button>(R.id.btnCalendar).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        findViewById<Button>(R.id.btnEvents).setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        findViewById<Button>(R.id.btnMessages).setOnClickListener {
            startActivity(Intent(this, ConversationsActivity::class.java))
        }
        findViewById<Button>(R.id.btnPayments).setOnClickListener {
            startActivity(Intent(this, PaymentsActivity::class.java))
        }
        findViewById<Button>(R.id.btnTransport).setOnClickListener {
            startActivity(Intent(this, TransportActivity::class.java))
        }
        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.btnAcademicInfo).setOnClickListener {
            startActivity(Intent(this, AcademicInfoActivity::class.java))
        }
    }
}
