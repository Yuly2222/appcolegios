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
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        // Obtener rol desde Firestore y redirigir al startDestination apropiado
                        val user = auth.currentUser
                        if (user == null) {
                            // Fallback
                            val intent = Intent(this, com.example.appcolegios.MainActivity::class.java)
                            intent.putExtra("startDestination", "home")
                            startActivity(intent)
                            finish()
                            return@addOnSuccessListener
                        }
                        firestore.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role")?.uppercase() ?: "ADMIN"
                                val displayName = doc.getString("displayName") ?: doc.getString("name") ?: ""
                                // Guardar en preferencias para sesiones persistentes
                                val userPrefs = UserPreferencesRepository(applicationContext)
                                lifecycleScope.launch {
                                    try {
                                        userPrefs.updateUserData(user.uid, role, displayName)
                                    } catch (_: Exception) {}
                                }
                                val startDestination = when (role) {
                                    // Admin ahora abre 'home' para ver la tarjeta de administración
                                    "ADMIN" -> "home"
                                    "DOCENTE" -> "teacher_home"
                                    "PADRE" -> "home"
                                    "ESTUDIANTE" -> "student_home"
                                    else -> "home"
                                }
                                val intent = Intent(this, com.example.appcolegios.MainActivity::class.java)
                                intent.putExtra("startDestination", startDestination)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                // Si falla al leer rol, abrir home por defecto
                                val intent = Intent(this, com.example.appcolegios.MainActivity::class.java)
                                intent.putExtra("startDestination", "home")
                                startActivity(intent)
                                finish()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        resetLink.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }
}
