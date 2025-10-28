package com.example.appcolegios.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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

        // Adapter personalizado para forzar color de texto (evita que las letras salgan en blanco en temas oscuros)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, roles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.BLACK)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.BLACK)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter

        // Si es desde admin no mostramos la confirmación de contraseña y password puede ser opcional
        if (fromAdmin) {
            // No ocultar los campos para que no parezcan "no funcionales".
            // Sólo avisamos con el hint de que la contraseña no se almacenará en este modo.
            passwordInput.hint = "Opcional (si se deja vacío se encola para backend)"
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
                // Intentamos crear la cuenta en Firebase Auth usando una FirebaseApp secundaria
                // para no afectar la sesión del admin.
                try {
                    val opts = FirebaseOptions.fromResource(this)
                    val importerApp = if (opts != null) {
                        try {
                            FirebaseApp.getInstance("importer")
                        } catch (_: IllegalStateException) {
                            FirebaseApp.initializeApp(this, opts, "importer")
                        }
                    } else {
                        null
                    }

                    val authImporter = if (importerApp != null) FirebaseAuth.getInstance(importerApp) else FirebaseAuth.getInstance()

                    if (password.isNotEmpty()) {
                        // Crear usuario en Auth (en la app importadora si fue posible)
                        authImporter.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { result ->
                                val uid = result.user?.uid
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
                                // Guardar documento asociado
                                db.collection(coll).document(uid ?: db.collection(coll).document().id)
                                    .set(data)
                                    .addOnSuccessListener {
                                        // Enviar verificación desde authImporter (no afecta admin)
                                        try {
                                            authImporter.currentUser?.sendEmailVerification()
                                        } catch (_: Exception) {}

                                        // Crear también documento en 'users' para que la app reconozca el rol y email
                                        try {
                                            val userMap = mapOf(
                                                "displayName" to name,
                                                "email" to email,
                                                "role" to role
                                            )
                                            db.collection("users").document(uid ?: db.collection("users").document().id)
                                                .set(userMap)
                                        } catch (_: Exception) {}

                                        // Cerrar sesión en importer para limpiar
                                        try { authImporter.signOut() } catch (_: Exception) {}

                                        Toast.makeText(this, "Usuario creado correctamente (Auth + Firestore)", Toast.LENGTH_LONG).show()
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error guardando documento: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                if (e is FirebaseAuthUserCollisionException) {
                                    Toast.makeText(this, "El correo ya está registrado.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "Error creando usuario en Auth: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        // Si no se especifica contraseña, encolamos la petición para backend (auth_queue)
                        val queueData = mapOf(
                            "email" to email,
                            "role" to role,
                            "displayName" to name,
                            "requestedAt" to com.google.firebase.Timestamp.now()
                        )
                        val coll = when (role) {
                            "PADRE" -> "parents"
                            "DOCENTE" -> "teachers"
                            "ADMIN" -> "admins"
                            else -> "students"
                        }
                        db.collection(coll).add(mapOf(
                            "name" to name,
                            "email" to email,
                            "document" to document,
                            "phone" to phone,
                            "role" to role,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )).addOnSuccessListener {
                            db.collection("auth_queue").add(queueData)
                                .addOnSuccessListener {
                                    // Crear también doc en 'users' para que la app conozca el correo/rol
                                    try {
                                        val userMap = mapOf(
                                            "displayName" to name,
                                            "email" to email,
                                            "role" to role,
                                            "importedAt" to com.google.firebase.Timestamp.now()
                                        )
                                        db.collection("users").add(userMap)
                                    } catch (_: Exception) {}

                                    Toast.makeText(this, "Usuario creado en Firestore y encolado para Auth (backend)", Toast.LENGTH_LONG).show()
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error encolando para Auth: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Error guardando documento: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Error inicializando importador Auth: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
