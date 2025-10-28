package com.example.appcolegios.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun VerifyEmailScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verifica tu correo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Te hemos enviado un correo de verificación. Por favor revisa tu bandeja y pulsa el enlace para activar tu cuenta.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            // Reenviar correo de verificación
            scope.launch {
                try {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null && !user.isEmailVerified) {
                        user.sendEmailVerification()
                        Toast.makeText(context, "Correo de verificación reenviado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Usuario no disponible o ya verificado", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al reenviar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("Reenviar correo")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onDone() }) {
            Text("Volver al inicio")
        }
    }
}

