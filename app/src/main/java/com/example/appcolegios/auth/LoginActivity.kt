package com.example.appcolegios.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appcolegios.R
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import com.example.appcolegios.data.UserPreferencesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        val emailInput: EditText = findViewById(R.id.emailInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val loginButton: MaterialButton = findViewById(R.id.loginButton)
        val resetLink: TextView = findViewById(R.id.resetLink)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Ingresa email y contraseña", Toast.LENGTH_SHORT).show()
            } else {
                // Usar coroutines para control más sencillo y fallback
                lifecycleScope.launch {
                    try {
                        val result = auth.signInWithEmailAndPassword(email, password).await()
                        val user = result.user
                        if (user == null) {
                            val intent = Intent(this@LoginActivity, com.example.appcolegios.MainActivity::class.java)
                            intent.putExtra("startDestination", "home")
                            startActivity(intent)
                            finish()
                            return@launch
                        }

                        // Verificar email
                        if (!user.isEmailVerified) {
                            auth.signOut()
                            Toast.makeText(this@LoginActivity, "Por favor verifica tu correo antes de iniciar sesión.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // Intentar obtener role y displayName desde 'users' y, si no está, desde colecciones específicas
                        val usersDoc = firestore.collection("users").document(user.uid).get().await()
                        var role = usersDoc.getString("role")?.uppercase() ?: ""
                        var displayName = usersDoc.getString("displayName") ?: usersDoc.getString("name") ?: ""

                        if (role.isBlank() || displayName.isBlank()) {
                            val collections = listOf("students", "teachers", "parents", "admins")
                            for (coll in collections) {
                                val doc = firestore.collection(coll).document(user.uid).get().await()
                                if (doc.exists()) {
                                    role = doc.getString("role") ?: when (coll) {
                                        "students" -> "ESTUDIANTE"
                                        "teachers" -> "DOCENTE"
                                        "parents" -> "PADRE"
                                        "admins" -> "ADMIN"
                                        else -> "ADMIN"
                                    }
                                    displayName = (doc.getString("nombres") ?: doc.getString("name")) ?: displayName
                                    break
                                }
                            }
                            if (displayName.isBlank()) {
                                val emailStr = user.email
                                if (!emailStr.isNullOrBlank()) {
                                    for (coll in listOf("students", "teachers", "parents", "admins")) {
                                        val query = firestore.collection(coll).whereEqualTo("email", emailStr).limit(1).get().await()
                                        if (!query.isEmpty) {
                                            val d = query.documents[0]
                                            role = d.getString("role") ?: role
                                            displayName = (d.getString("nombres") ?: d.getString("name")) ?: displayName
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        if (role.isBlank()) role = "ADMIN"

                        // Guardar en preferencias para sesiones persistentes
                        val userPrefs = UserPreferencesRepository(applicationContext)
                        withContext(Dispatchers.IO) {
                            try {
                                userPrefs.updateUserData(user.uid, role, displayName)
                            } catch (_: Exception) {}
                        }

                        val startDestination = when (role.uppercase()) {
                            "ADMIN" -> "home"
                            "DOCENTE" -> "teacher_home"
                            "PADRE" -> "home"
                            "ESTUDIANTE" -> "student_home"
                            else -> "home"
                        }

                        val intent = Intent(this@LoginActivity, com.example.appcolegios.MainActivity::class.java)
                        intent.putExtra("startDestination", startDestination)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
             }
         }
        resetLink.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }
}
