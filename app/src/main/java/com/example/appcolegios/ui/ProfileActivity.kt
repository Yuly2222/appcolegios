package com.example.appcolegios.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.appcolegios.R
import com.example.appcolegios.perfil.AcademicInfoActivity
import com.example.appcolegios.perfil.ProfileViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileVm: ProfileViewModel
    private lateinit var progressBar: ProgressBar

    // Views
    private lateinit var profileImage: ImageView
    private lateinit var nameText: TextView
    private lateinit var courseText: TextView
    private lateinit var listNumberText: TextView
    private lateinit var emailText: TextView
    private lateinit var roleText: TextView
    private lateinit var averageText: TextView
    private lateinit var academicInfoButton: Button

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profileVm = ViewModelProvider(this).get(ProfileViewModel::class.java)

        // Registrar el launcher aquí, una vez que profileVm está inicializado
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                profileVm.uploadStudentPhotoAsBase64WithResolver(contentResolver, it) { dataUri, error ->
                    runOnUiThread {
                        if (!dataUri.isNullOrBlank()) {
                            Toast.makeText(this, "Imagen de perfil actualizada", Toast.LENGTH_SHORT).show()
                            profileVm.refreshAllData()
                        } else {
                            Toast.makeText(this, error ?: "Error al subir imagen", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        initViews()
        setupObservers()

        profileImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        academicInfoButton.setOnClickListener {
            startActivity(Intent(this, AcademicInfoActivity::class.java))
        }
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar) ?: ProgressBar(this).apply { visibility = View.GONE }
        profileImage = findViewById(R.id.profileImage)
        nameText = findViewById(R.id.nameText)
        courseText = findViewById(R.id.courseText)
        listNumberText = findViewById(R.id.listNumberText)
        emailText = findViewById(R.id.emailText)
        roleText = findViewById(R.id.roleText)
        averageText = findViewById(R.id.averageText)
        academicInfoButton = findViewById(R.id.academicInfoButton)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            profileVm.student.collectLatest { result ->
                progressBar.visibility = View.GONE
                result?.onSuccess { student ->
                    if (student != null) {
                        updateUi(student)
                    } else {
                        showEmptyState()
                    }
                }?.onFailure {
                    showErrorState(it.message)
                }
            }
        }

        lifecycleScope.launch {
            profileVm.roleString.collectLatest { role ->
                roleText.text = getString(R.string.role_label, role?.uppercase() ?: "N/A")
                val isAdmin = role.equals("ADMIN", ignoreCase = true)
                findViewById<View>(R.id.academicCard)?.visibility = if (isAdmin) View.GONE else View.VISIBLE
                academicInfoButton.visibility = if (isAdmin) View.GONE else View.VISIBLE
            }
        }
    }

    private fun updateUi(student: com.example.appcolegios.data.model.Student) {
        nameText.text = student.nombre.ifBlank { getString(R.string.no_student_data) }
        courseText.text = getString(R.string.course_group_format, student.curso.ifBlank { "N/A" }, student.grupo.ifBlank { "N/A" })
        listNumberText.text = if (student.numeroLista > 0) student.numeroLista.toString() else "N/A"
        emailText.text = student.correoInstitucional.ifBlank { "N/A" }
        averageText.text = if (student.promedio > 0.0) String.format(Locale.getDefault(), "%.2f", student.promedio) else "N/A"

        if (!student.photoUrl.isNullOrBlank()) {
            profileImage.load(student.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_foreground)
            }
        } else {
            profileImage.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun showEmptyState() {
        nameText.text = getString(R.string.no_student_data)
        courseText.text = ""
        listNumberText.text = ""
        emailText.text = ""
        averageText.text = ""
        profileImage.setImageResource(R.drawable.ic_launcher_foreground)
    }

    private fun showErrorState(message: String?) {
        nameText.text = getString(R.string.error_cargando_datos)
        Toast.makeText(this, message ?: "Error desconocido", Toast.LENGTH_LONG).show()
    }
}
