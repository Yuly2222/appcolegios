package com.example.appcolegios.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import java.util.Locale

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val fromAdmin = intent.getBooleanExtra("fromAdmin", false)

        val nameInput: EditText = findViewById(R.id.nameInput)
        val emailInput: EditText = findViewById(R.id.emailInput)
        val documentInput: EditText = findViewById(R.id.documentInput)
        val phoneInput: EditText = findViewById(R.id.phoneInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val confirmInput: EditText = findViewById(R.id.confirmPasswordInput)
        val roleSpinner: Spinner = findViewById(R.id.roleSpinner)
        val registerButton: MaterialButton = findViewById(R.id.registerButton)

        // Si viene de admin permitir seleccionar ADMIN y ocultar campos de contraseña opcionalmente
        val roles = if (fromAdmin) listOf("ESTUDIANTE", "PADRE", "DOCENTE", "ADMIN") else listOf("ESTUDIANTE", "PADRE", "DOCENTE")
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        // Si es desde admin no mostramos la confirmación de contraseña y password puede ser opcional
        if (fromAdmin) {
            // No ocultar los campos para que no parezcan "no funcionales".
            // Sólo avisamos con el hint de que la contraseña no se almacenará en este modo.
            passwordInput.hint = "Opcional (no se guarda en modo admin)"
            confirmInput.hint = "Opcional"
        }

        registerButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val document = documentInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirm = confirmInput.text.toString().trim()
            // Obtener rol seleccionado y normalizar a mayúsculas; si está vacío, asumir ADMIN
            val rawRole = roles.getOrNull(roleSpinner.selectedItemPosition)
            val role = rawRole?.uppercase(Locale.ROOT) ?: "ADMIN"

            if (name.isEmpty() || email.isEmpty() || document.isEmpty() || phone.isEmpty() || (!fromAdmin && (password.isEmpty() || confirm.isEmpty()))) {
                Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!fromAdmin && (password.length < 6 || password != confirm)) {
                Toast.makeText(this, "Contraseñas inválidas", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (fromAdmin) {
                // Crear solo documento en Firestore, no tocar FirebaseAuth para no afectar la sesión actual
                val data = mapOf(
                    "name" to name,
                    "email" to email,
                    "document" to document,
                    "phone" to phone,
                    "role" to role,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                val coll = when (role) {
                    "PADRE" -> "parents"
                    "DOCENTE" -> "teachers"
                    "ADMIN" -> "admins"
                    else -> "students"
                }
                db.collection(coll)
                    .add(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Usuario creado correctamente", Toast.LENGTH_LONG).show()
                        // Volver a la pantalla anterior (Admin)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // Flujo normal: registrar en FirebaseAuth y crear documento en Firestore luego cerrar sesión y volver al login
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: return@addOnSuccessListener
                        val data = mapOf(
                            "name" to name,
                            "email" to email,
                            "document" to document,
                            "phone" to phone,
                            "role" to role
                        )
                        db.collection("users").document(uid).set(data)
                            .addOnCompleteListener {
                                // Mantener comportamiento previo: desloguear y volver al login
                                auth.signOut()
                                Toast.makeText(this, "Cuenta creada. Inicia sesión.", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
}
