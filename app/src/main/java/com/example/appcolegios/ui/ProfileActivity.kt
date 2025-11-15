package com.example.appcolegios.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.appcolegios.R
import com.example.appcolegios.perfil.AcademicInfoActivity
import com.example.appcolegios.perfil.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    // Parent-specific views
    private lateinit var parentCard: View
    private lateinit var parentChildText: TextView
    private lateinit var selectChildButton: Button
    private lateinit var parentEmailText: TextView
    private lateinit var parentPhoneEdit: EditText
    private lateinit var saveParentPhoneButton: Button
    private lateinit var addInfoButton: Button

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
                            Toast.makeText(this@ProfileActivity, getString(R.string.profile_image_updated), Toast.LENGTH_SHORT).show()
                            profileVm.refreshAllData()
                        } else {
                            Toast.makeText(this@ProfileActivity, error ?: getString(R.string.error_subiendo_imagen), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        initViews()
        setupObservers()
        setupParentInteractions()

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

        parentCard = findViewById(R.id.parentCard)
        parentChildText = findViewById(R.id.parentChildText)
        selectChildButton = findViewById(R.id.selectChildButton)
        parentEmailText = findViewById(R.id.parentEmailText)
        parentPhoneEdit = findViewById(R.id.parentPhoneEdit)
        saveParentPhoneButton = findViewById(R.id.saveParentPhoneButton)
        addInfoButton = findViewById(R.id.addInfoButton)
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
                // Mostrar u ocultar tarjeta académica para ADMIN
                findViewById<View>(R.id.academicCard)?.visibility = if (isAdmin) View.GONE else View.VISIBLE

                // Si es PADRE: ocultar vista académica y mostrar parentCard
                if (role.equals("PADRE", ignoreCase = true)) {
                    findViewById<View>(R.id.academicCard)?.visibility = View.GONE
                    parentCard.visibility = View.VISIBLE

                    // Forzar visibilidad de los botones parentales
                    selectChildButton.visibility = View.VISIBLE
                    addInfoButton.visibility = View.VISIBLE

                    // Habilitar/deshabilitar según si ya tenemos hijos cargados
                    val hasChildren = profileVm.children.value.isNotEmpty()
                    selectChildButton.isEnabled = hasChildren
                    addInfoButton.isEnabled = hasChildren

                    // completar email del padre con el email de auth (no editable)
                    parentEmailText.text = auth.currentUser?.email ?: getString(R.string.sample_email)

                    // intentar prefill teléfono desde users/{uid}
                    lifecycleScope.launch {
                        val uid = auth.currentUser?.uid
                        if (!uid.isNullOrBlank()) {
                            try {
                                val doc = db.collection("users").document(uid).get().await()
                                if (doc.exists()) {
                                    val phone = doc.getString("phone")
                                    parentPhoneEdit.setText(phone ?: "")
                                }
                            } catch (_: Exception) { /* ignore */ }
                        }
                    }
                } else {
                    // no es padre -> ocultar parentCard y botones
                    parentCard.visibility = View.GONE
                    selectChildButton.visibility = View.GONE
                    addInfoButton.visibility = View.GONE
                }
            }
        }

        // Observador de hijos para actualizar el selector
        lifecycleScope.launch {
            profileVm.children.collectLatest { list ->
                if (list.isEmpty()) {
                    parentChildText.text = getString(R.string.no_children)
                    selectChildButton.isEnabled = false
                    addInfoButton.isEnabled = false
                } else {
                    selectChildButton.isEnabled = true
                    addInfoButton.isEnabled = true
                    // Si el rol actual es PADRE, asegurarse que los botones sean visibles
                    try {
                        if (profileVm.roleString.value.equals("PADRE", ignoreCase = true)) {
                            selectChildButton.visibility = View.VISIBLE
                            addInfoButton.visibility = View.VISIBLE
                        }
                    } catch (_: Exception) { /* Si roleString no es accesible por .value, ignoramos */ }

                    val idx = profileVm.selectedChildIndex.value ?: 0
                    val child = list.getOrNull(idx) ?: list.first()
                    parentChildText.text = child.nombre.ifBlank { getString(R.string.no_student_data) }
                }
            }
        }
    }

    private fun setupParentInteractions() {
        // Selección de hijo
        selectChildButton.setOnClickListener {
            lifecycleScope.launch {
                val list = profileVm.children.value
                if (list.isEmpty()) return@launch
                val names = list.map { it.nombre.ifBlank { getString(R.string.no_student_data) } }.toTypedArray()
                val current = profileVm.selectedChildIndex.value ?: 0
                var selectedIndex = current
                AlertDialog.Builder(this@ProfileActivity)
                    .setTitle(getString(R.string.select_child_title))
                    .setSingleChoiceItems(names, current) { _, which -> selectedIndex = which }
                    .setPositiveButton(getString(R.string.select)) { dialog, _ ->
                        profileVm.selectChildAtIndex(selectedIndex)
                        parentChildText.text = list.getOrNull(selectedIndex)?.nombre ?: getString(R.string.no_children)
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }

        // Guardar teléfono del padre en users/{uid}
        saveParentPhoneButton.setOnClickListener {
            val phone = parentPhoneEdit.text.toString().trim()
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(this@ProfileActivity, getString(R.string.must_be_logged_in), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = mapOf("phone" to phone)
            db.collection("users").document(uid).set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this@ProfileActivity, getString(R.string.phone_saved), Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@ProfileActivity, getString(R.string.error_guardando, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
        }

        // Agregar información del hijo: abrir dialog con campos y guardar en students/{childId}/parentInfo
        addInfoButton.setOnClickListener {
            val list = profileVm.children.value
            val idx = profileVm.selectedChildIndex.value ?: 0
            val child = list.getOrNull(idx)
            if (child == null) {
                Toast.makeText(this@ProfileActivity, getString(R.string.no_child_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddInfoDialog(child.id)
        }
    }

    private fun showAddInfoDialog(childId: String) {
        // Inflar la vista del diálogo (layout actualizado con secciones y campos)
        val dlgView = layoutInflater.inflate(R.layout.dialog_add_info_parent, null)

        // Helper para obtener ids dinámicamente (evita errores de análisis si R no está regenerado)
        fun id(name: String) = resources.getIdentifier(name, "id", packageName)

        // Encontrar todos los campos por id (nombres descriptivos)
        val etHealthConditions = dlgView.findViewById<EditText>(id("et_health_conditions"))
        val etAllergies = dlgView.findViewById<EditText>(id("et_allergies"))
        val etMedications = dlgView.findViewById<EditText>(id("et_medications"))
        val etMedRestrictions = dlgView.findViewById<EditText>(id("et_med_restrictions"))
        val etEmergencyInstructions = dlgView.findViewById<EditText>(id("et_emergency_instructions"))

        val etAuthorizedPeople = dlgView.findViewById<EditText>(id("et_authorized_people"))
        val etNotAuthorizedPeople = dlgView.findViewById<EditText>(id("et_not_authorized_people"))
        val etSpecialAuthorizations = dlgView.findViewById<EditText>(id("et_special_authorizations"))

        val contact1Name = dlgView.findViewById<EditText>(id("contact1_name"))
        val contact1Relation = dlgView.findViewById<EditText>(id("contact1_relation"))
        val contact1Phone = dlgView.findViewById<EditText>(id("contact1_phone"))

        val contact2Name = dlgView.findViewById<EditText>(id("contact2_name"))
        val contact2Relation = dlgView.findViewById<EditText>(id("contact2_relation"))
        val contact2Phone = dlgView.findViewById<EditText>(id("contact2_phone"))

        val etLearningDifficulties = dlgView.findViewById<EditText>(id("et_learning_difficulties"))
        val etRecommendations = dlgView.findViewById<EditText>(id("et_recommendations"))
        val etEmotionalSupport = dlgView.findViewById<EditText>(id("et_emotional_support"))

        val etFoodPreferences = dlgView.findViewById<EditText>(id("et_food_preferences"))
        val etRoutines = dlgView.findViewById<EditText>(id("et_routines"))
        val etAdditionalObservations = dlgView.findViewById<EditText>(id("et_additional_observations"))

        // Construir y mostrar el diálogo. Sin llamadas a backend: solo recolectar y pasar a callback local.
        AlertDialog.Builder(this@ProfileActivity)
            .setTitle(getString(R.string.add_info_title))
            .setView(dlgView)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                // Recolectar los valores en un mapa
                val parentInfoMap = mapOf(
                    "medicalInfo" to (etHealthConditions?.text?.toString()?.trim() ?: ""),
                    "allergies" to (etAllergies?.text?.toString()?.trim() ?: ""),
                    "medications" to (etMedications?.text?.toString()?.trim() ?: ""),
                    "medicalRestrictions" to (etMedRestrictions?.text?.toString()?.trim() ?: ""),
                    "emergencyInstructions" to (etEmergencyInstructions?.text?.toString()?.trim() ?: ""),

                    "authorizedPeople" to (etAuthorizedPeople?.text?.toString()?.trim() ?: ""),
                    "notAuthorizedPeople" to (etNotAuthorizedPeople?.text?.toString()?.trim() ?: ""),
                    "specialAuthorizations" to (etSpecialAuthorizations?.text?.toString()?.trim() ?: ""),

                    "contact1Name" to (contact1Name?.text?.toString()?.trim() ?: ""),
                    "contact1Relation" to (contact1Relation?.text?.toString()?.trim() ?: ""),
                    "contact1Phone" to (contact1Phone?.text?.toString()?.trim() ?: ""),

                    "contact2Name" to (contact2Name?.text?.toString()?.trim() ?: ""),
                    "contact2Relation" to (contact2Relation?.text?.toString()?.trim() ?: ""),
                    "contact2Phone" to (contact2Phone?.text?.toString()?.trim() ?: ""),

                    "learningDifficulties" to (etLearningDifficulties?.text?.toString()?.trim() ?: ""),
                    "recommendations" to (etRecommendations?.text?.toString()?.trim() ?: ""),
                    "emotionalSupport" to (etEmotionalSupport?.text?.toString()?.trim() ?: ""),

                    "foodPreferences" to (etFoodPreferences?.text?.toString()?.trim() ?: ""),
                    "routines" to (etRoutines?.text?.toString()?.trim() ?: ""),
                    "additionalObservations" to (etAdditionalObservations?.text?.toString()?.trim() ?: "")
                )

                // Llamada local que representa el "callback" de guardar; sólo UI (Toast) y posible extensión futura.
                handleParentInfoSubmission(childId, parentInfoMap)

                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Callback local: manejar la información recolectada (UI-only).
    private fun handleParentInfoSubmission(childId: String, info: Map<String, String>) {
        // Actualmente no se implementa backend. Aquí mostramos un Toast confirmando la captura
        // y dejamos el mapa disponible para que puedas implementarlo después (por ejemplo enviar al servidor).
        val resumen = mutableListOf<String>()
        // Añadir algunos campos clave al resumen para feedback rápido
        info["contact1Phone"]?.takeIf { it.isNotBlank() }?.let { resumen.add("Contacto1: $it") }
        info["allergies"]?.takeIf { it.isNotBlank() }?.let { resumen.add("Alergias: ${it.take(30)}${if (it.length>30) "..." else ""}") }

        // Usar childId en el resumen de manera segura (muestra sólo primeros caracteres)
        val idShort = if (childId.length > 6) childId.take(6) + "..." else childId
        if (idShort.isNotBlank()) resumen.add("Estudiante: $idShort")

        val toastMsg = if (resumen.isEmpty()) getString(R.string.info_saved) else getString(R.string.info_saved) + " (" + resumen.joinToString(", ") + ")"
        Toast.makeText(this@ProfileActivity, toastMsg, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this@ProfileActivity, message ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
    }
}
