package com.example.appcolegios.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.example.appcolegios.perfil.AcademicInfoActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ProfileActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar

    // Views
    private lateinit var profileImage: ImageView
    private lateinit var nameText: TextView
    private lateinit var courseText: TextView
    private lateinit var listNumberText: TextView
    private lateinit var birthdateText: TextView
    private lateinit var bloodTypeText: TextView
    private lateinit var emailText: TextView
    private lateinit var guardianText: TextView
    private lateinit var phoneText: TextView
    private lateinit var addressText: TextView
    private lateinit var epsText: TextView
    private lateinit var enrollmentStatusText: TextView
    private lateinit var averageText: TextView
    private lateinit var academicInfoButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initViews()
        // Antes de cargar datos académicos, comprobamos el rol para ocultar la sección si es ADMIN
        lifecycleScope.launch {
            val repo = UserPreferencesRepository(applicationContext)
            var roleString: String? = null
            try {
                val userData = repo.userData.first()
                roleString = userData.role
            } catch (_: Exception) { }

            val isAdmin = (roleString ?: "ADMIN").equals("ADMIN", ignoreCase = true)
            runOnUiThread {
                val academicCard = findViewById<View?>(R.id.academicCard)
                if (isAdmin) {
                    academicCard?.visibility = View.GONE
                    academicInfoButton.visibility = View.GONE
                } else {
                    academicCard?.visibility = View.VISIBLE
                    academicInfoButton.visibility = View.VISIBLE
                }
            }
        }

        loadStudentData()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar) ?: ProgressBar(this)
        profileImage = findViewById(R.id.profileImage)
        nameText = findViewById(R.id.nameText)
        courseText = findViewById(R.id.courseText)
        listNumberText = findViewById(R.id.listNumberText)
        birthdateText = findViewById(R.id.birthdateText)
        bloodTypeText = findViewById(R.id.bloodTypeText)
        emailText = findViewById(R.id.emailText)
        guardianText = findViewById(R.id.guardianText)
        phoneText = findViewById(R.id.phoneText)
        addressText = findViewById(R.id.addressText)
        epsText = findViewById(R.id.epsText)
        enrollmentStatusText = findViewById(R.id.enrollmentStatusText)
        averageText = findViewById(R.id.averageText)
        academicInfoButton = findViewById(R.id.academicInfoButton)

        academicInfoButton.setOnClickListener {
            startActivity(Intent(this, AcademicInfoActivity::class.java))
        }
    }

    private fun loadStudentData() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("students").document(userId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    // Datos básicos
                    nameText.text = document.getString("nombre") ?: "No registrado"
                    courseText.text = "${document.getString("curso") ?: "N/A"} - Grupo ${document.getString("grupo") ?: "N/A"}"
                    listNumberText.text = document.getLong("numeroLista")?.toString() ?: "No registrado"

                    // Fecha de nacimiento
                    val birthdate = document.getTimestamp("fechaNacimiento")?.toDate()
                    birthdateText.text = if (birthdate != null) {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(birthdate)
                    } else {
                        "No registrado"
                    }

                    bloodTypeText.text = document.getString("grupoSanguineo") ?: "No registrado"
                    emailText.text = document.getString("correoInstitucional") ?: auth.currentUser?.email ?: "No registrado"

                    // Acudiente
                    val guardian = document.getString("acudiente")
                    guardianText.text = guardian ?: "No registrado"

                    phoneText.text = document.getString("telefono") ?: "No registrado"
                    addressText.text = document.getString("direccion") ?: "No registrado"
                    epsText.text = document.getString("eps") ?: "No registrado"
                    enrollmentStatusText.text = document.getString("estadoMatricula") ?: "Activo"

                    // Promedio
                    val average = document.getDouble("promedio")
                    averageText.text = if (average != null) {
                        String.format("%.2f", average)
                    } else {
                        "N/A"
                    }

                } else {
                    nameText.text = "Estudiante no encontrado"
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                nameText.text = "Error al cargar datos: ${e.message}"
            }
    }
}
