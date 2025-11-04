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
    private lateinit var roleText: TextView

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

        // Cargar y mostrar rol desde Firestore
        loadAndShowRole()

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
        roleText = findViewById(R.id.roleText)

        academicInfoButton.setOnClickListener {
            startActivity(Intent(this, AcademicInfoActivity::class.java))
        }
    }

    private fun loadAndShowRole() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { udoc ->
                var roleVal = udoc.getString("role")
                if (roleVal.isNullOrBlank()) {
                    val collections = listOf("students", "teachers", "parents", "admins")
                    var resolved: String? = null
                    var pending = collections.size
                    for (c in collections) {
                        firestore.collection(c).document(uid).get().addOnSuccessListener { d ->
                            if (resolved == null && d.exists()) {
                                resolved = d.getString("role") ?: when (c) {
                                    "students" -> "ESTUDIANTE"
                                    "teachers" -> "DOCENTE"
                                    "parents" -> "PADRE"
                                    "admins" -> "ADMIN"
                                    else -> null
                                }
                                resolved?.let { roleText.text = getString(R.string.role_label, it.uppercase()) }
                            }
                        }.addOnCompleteListener {
                            pending -= 1
                            if (pending == 0 && resolved == null) {
                                roleText.text = getString(R.string.role_na)
                            }
                        }
                    }
                } else {
                    roleText.text = getString(R.string.role_label, roleVal.uppercase())
                }
            }
            .addOnFailureListener { _ -> roleText.text = getString(R.string.role_na) }
    }

    private fun loadStudentData() {
        val userId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("students").document(userId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    nameText.text = document.getString("nombre") ?: getString(R.string.no_student_data)
                    val curso = document.getString("curso") ?: "N/A"
                    val grupo = document.getString("grupo") ?: "N/A"
                    courseText.text = getString(R.string.course_group_format, curso, grupo)
                    listNumberText.text = document.getLong("numeroLista")?.toString() ?: getString(R.string.no_student_data)

                    val birthdate = document.getTimestamp("fechaNacimiento")?.toDate()
                    birthdateText.text = if (birthdate != null) {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(birthdate)
                    } else {
                        getString(R.string.no_student_data)
                    }

                    bloodTypeText.text = document.getString("grupoSanguineo") ?: getString(R.string.no_student_data)
                    emailText.text = document.getString("correoInstitucional") ?: auth.currentUser?.email ?: getString(R.string.no_student_data)

                    val guardian = document.getString("acudiente")
                    guardianText.text = guardian ?: getString(R.string.no_student_data)

                    phoneText.text = document.getString("telefono") ?: getString(R.string.no_student_data)
                    addressText.text = document.getString("direccion") ?: getString(R.string.no_student_data)
                    epsText.text = document.getString("eps") ?: getString(R.string.no_student_data)
                    enrollmentStatusText.text = document.getString("estadoMatricula") ?: getString(R.string.sample_enrollment_status)

                    val average = document.getDouble("promedio")
                    averageText.text = if (average != null) {
                        String.format(Locale.getDefault(), "%.2f", average)
                    } else {
                        getString(R.string.sample_average)
                    }

                } else {
                    nameText.text = getString(R.string.no_student_data)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                nameText.text = getString(R.string.error_label) + ": " + (e.message ?: "")
            }
    }
}
