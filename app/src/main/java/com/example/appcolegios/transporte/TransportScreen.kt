package com.example.appcolegios.transporte

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.net.URLEncoder
import com.google.android.gms.location.LocationServices
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.example.appcolegios.R
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.appcolegios.util.rememberMapViewWithLifecycle
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun TransportScreen() {
    val medios = listOf("A pie", "Bicicleta", "Transporte público", "Vehículo particular")
    var seleccionado by remember { mutableStateOf(medios.first()) }
    var estado by remember { mutableStateOf<Estado>(Estado.Idle) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Estados para el diálogo de confirmación
    var showConfirmDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf<String?>(null) }

    // Mantener modo pendiente si el permiso se solicita y luego el usuario lo concede
    var pendingRouteMode by remember { mutableStateOf<String?>(null) }

    // Implementación separada para realizar la petición y lanzar Maps (usa fused location)
    val fetchAndLaunchRoute: (String) -> Unit = { modo ->
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc == null) {
                    scope.launch { snackbarHostState.showSnackbar("No se pudo obtener la ubicación. Asegúrate de que el GPS esté activo.") }
                    return@addOnSuccessListener
                }
                val origin = "${loc.latitude},${loc.longitude}"
                val destination = try { URLEncoder.encode("Universidad de La Sabana, Chía, Colombia", "UTF-8") } catch (_: Exception) { "Universidad+de+La+Sabana+Chia+Colombia" }
                val travelMode = when (modo) {
                    "A pie" -> "walking"
                    "Bicicleta" -> "bicycling"
                    "Transporte público" -> "transit"
                    else -> "driving"
                }
                val uri = "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=$travelMode".toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri)
                // Intentar abrir con la app de Google Maps
                intent.setPackage("com.google.android.apps.maps")
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback al navegador si Maps no está instalado
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }.addOnFailureListener { e ->
                scope.launch { snackbarHostState.showSnackbar("Error obteniendo ubicación: ${e.localizedMessage}") }
            }
        } catch (e: SecurityException) {
            // permiso revocado durante la ejecución
            scope.launch { snackbarHostState.showSnackbar("Permiso de ubicación no disponible") }
        }
    }

    // Launcher para pedir permiso de ubicación
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            // Si había una ruta pendiente, abrirla automáticamente
            val modo = pendingRouteMode
            if (modo != null) {
                pendingRouteMode = null
                fetchAndLaunchRoute(modo)
            } else {
                scope.launch { snackbarHostState.showSnackbar("Permiso de ubicación concedido") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Permiso de ubicación denegado") }
        }
    }

    // Función para obtener ubicación y abrir direcciones en Google Maps
    val openRoute: (String) -> Unit = { modo ->
        // Comprobar permiso
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Guardar el modo solicitado y solicitar permiso; al conceder se abrirá la ruta
            pendingRouteMode = modo
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            scope.launch { snackbarHostState.showSnackbar("Se solicitó permiso. Si lo concediste, la ruta se abrirá automáticamente.") }
        } else {
            // Permiso ya concedido, proceder a obtener ubicación y abrir Maps
            fetchAndLaunchRoute(modo)
        }
    }

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

        // SnackbarHost para mostrar mensajes
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxWidth())

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
                    // Ejecutamos la geocodificación sin usar la API sincronizada y deprecada
                    scope.launch {
                        try {
                            val uniLatLng = geocodeLocation(context, "Universidad de La Sabana, Chía, Colombia") ?: colegio
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(uniLatLng, 15f))
                            map.clear()
                            map.addMarker(MarkerOptions().position(uniLatLng).title("Universidad de La Sabana").icon(BitmapDescriptorFactory.defaultMarker()))
                        } catch (e: Exception) {
                            // Si falla la geocodificación, usamos las coordenadas de fallback y mostramos snackbar
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(colegio, 15f))
                            map.clear()
                            map.addMarker(MarkerOptions().position(colegio).title("Colegio (fallback)").icon(BitmapDescriptorFactory.defaultMarker()))
                            scope.launch { snackbarHostState.showSnackbar("No se pudo geocodificar la dirección: ${e.localizedMessage}") }
                        }
                    }
                }
            }
        }

        medios.forEach { medio ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = seleccionado == medio,
                    onClick = {
                        // Asignar inmediatamente la nueva selección
                        seleccionado = medio
                        // Mostrar diálogo de confirmación (no mostramos el modo en el diálogo)
                        dialogMode = medio
                        showConfirmDialog = true
                    }
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
                        db.collection("transportAttendance").add(data).addOnSuccessListener {
                            estado = Estado.Success
                        }.addOnFailureListener { e ->
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

    // Diálogo de confirmación
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                // Al descartar fuera del diálogo, cerramos y limpiamos estados temporales (no revertimos la selección)
                dialogMode = null
                showConfirmDialog = false
            },
            title = { Text("Ver ruta y duración") },
            text = {
                // No mostrar el modo según tu petición
                Text("¿Deseas ver la duración del trayecto y la ruta en Google Maps?")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Confirmar: la selección ya fue aplicada; ejecutar openRoute
                    openRoute(dialogMode ?: "")
                    // limpiar estados temporales
                    dialogMode = null
                    showConfirmDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Cancelar: cerrar el diálogo y mantener la selección que el usuario dejó marcada
                    dialogMode = null
                    showConfirmDialog = false
                }) { Text("Cancelar") }
            }
        )
    }
}

sealed interface Estado {
    object Idle : Estado
    object Loading : Estado
    object Success : Estado
    data class Error(val mensaje: String) : Estado
}

// Helper suspend para geocodificar de forma segura según versión de Android
suspend fun geocodeLocation(context: Context, locationName: String): LatLng? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
            try {
                val geo = Geocoder(context, Locale.getDefault())
                geo.getFromLocationName(locationName, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        if (!cont.isActive) return
                        val a = addresses.firstOrNull()
                        if (a != null) cont.resume(LatLng(a.latitude, a.longitude)) else cont.resume(null)
                    }
                })
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            val geo = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geo.getFromLocationName(locationName, 1)
            if (!results.isNullOrEmpty()) LatLng(results[0].latitude, results[0].longitude) else null
        }
    }
}
