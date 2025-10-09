package com.example.appcolegios.transporte

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun TransportScreen() {
    val medios = listOf("A pie", "Bicicleta", "Transporte público", "Vehículo particular")
    var seleccionado by remember { mutableStateOf(medios.first()) }
    var estado by remember { mutableStateOf<Estado>(Estado.Idle) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Transporte (sin rutas escolares)", style = MaterialTheme.typography.headlineSmall)
        Text("Selecciona el medio con el que llegaste hoy y registra tu asistencia.")

        medios.forEach { medio ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = seleccionado == medio,
                    onClick = { seleccionado = medio }
                )
                Spacer(Modifier.width(8.dp))
                Text(medio)
            }
        }

        when (estado) {
            is Estado.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Registrando…")
                }
            }
            is Estado.Error -> {
                Text((estado as Estado.Error).mensaje, color = MaterialTheme.colorScheme.error)
            }
            is Estado.Success -> {
                Text("Asistencia registrada correctamente")
            }
            else -> {}
        }

        Button(
            enabled = estado !is Estado.Loading,
            onClick = {
                scope.launch {
                    estado = Estado.Loading
                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser
                    if (user == null) {
                        estado = Estado.Error("Debes iniciar sesión para registrar asistencia")
                        return@launch
                    }
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val data = hashMapOf(
                            "userId" to user.uid,
                            "medio" to seleccionado,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("transportAttendance").add(data)
                        estado = Estado.Success
                    } catch (e: Exception) {
                        estado = Estado.Error(e.localizedMessage ?: "Error desconocido")
                    }
                }
            }
        ) {
            Text("Registrar asistencia")
        }
    }
}

private sealed interface Estado {
    data object Idle : Estado
    data object Loading : Estado
    data object Success : Estado
    data class Error(val mensaje: String) : Estado
}
