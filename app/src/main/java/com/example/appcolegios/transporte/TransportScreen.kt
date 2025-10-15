package com.example.appcolegios.transporte

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.example.appcolegios.R
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.example.appcolegios.util.rememberMapViewWithLifecycle
import com.example.appcolegios.auth.LoginActivity

@Composable
fun TransportScreen() {
    val medios = listOf("A pie", "Bicicleta", "Transporte público", "Vehículo particular")
    var seleccionado by remember { mutableStateOf(medios.first()) }
    var estado by remember { mutableStateOf<Estado>(Estado.Idle) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Coordenadas del colegio (ejemplo)
    val colegio = remember { LatLng(4.7110, -74.0721) }

    // MapView
    val googleMapsKey = try { context.getString(R.string.google_maps_key) } catch (_: Exception) { "" }
    val hasMapsKey = googleMapsKey.isNotBlank() && !googleMapsKey.contains("REEMPLAZA_CON_TU_API_KEY")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Transporte (sin rutas escolares)", style = MaterialTheme.typography.headlineSmall)
        Text("Selecciona el medio con el que llegaste hoy y registra tu asistencia.")

        // Mostrar mapa solo si hay API key
        if (!hasMapsKey) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("API de Google Maps no configurada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text("Coloca tu API key en strings.xml (google_maps_key) para habilitar el mapa.")
                }
            }
        } else {
            val mapView = rememberMapViewWithLifecycle()

            AndroidView(factory = { mapView }, modifier = Modifier
                .height(220.dp)
                .fillMaxWidth()) { view ->
                view.getMapAsync { map ->
                    map.uiSettings.isZoomControlsEnabled = true
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(colegio, 15f))
                    map.clear()
                    map.addMarker(MarkerOptions().position(colegio).title("Colegio").icon(BitmapDescriptorFactory.defaultMarker()))
                }
            }
        }

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
                        // opcional: abrir login
                        return@launch
                    }
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val data = hashMapOf(
                            "userId" to user.uid,
                            "medio" to seleccionado,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("transportAttendance").add(data).addOnSuccessListener {
                            estado = Estado.Success
                        }.addOnFailureListener { e ->
                            // Añadir diagnóstico del proyecto y uid para facilitar debugging
                            val projectId = try { com.google.firebase.FirebaseApp.getInstance().options.projectId } catch (_: Exception) { null }
                            val diag = "uid=${user.uid}, project=${projectId ?: "unknown"}"
                            estado = Estado.Error("${e.localizedMessage ?: "Error desconocido"} ($diag)")
                        }
                    } catch (e: Exception) {
                        val projectId = try { com.google.firebase.FirebaseApp.getInstance().options.projectId } catch (_: Exception) { null }
                        val diag = "project=${projectId ?: "unknown"}"
                        estado = Estado.Error("${e.localizedMessage ?: "Error desconocido"} ($diag)")
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
