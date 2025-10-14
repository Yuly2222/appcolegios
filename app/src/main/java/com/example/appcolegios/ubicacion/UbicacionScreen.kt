package com.example.appcolegios.ubicacion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.appcolegios.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun UbicacionScreen() {
    val context = LocalContext.current

    // Coordenadas fijas del colegio (actualiza con las reales)
    val colegio = remember { LatLng(4.7110, -74.0721) }

    val mapView = rememberMapViewWithLifecycle()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            errorMessage = stringResource(R.string.activa_ubicacion_para_ruta)
        } else {
            centerOnMyLocation(fused, googleMap, onError = { msg -> errorMessage = msg })
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Mapa en modo lectura por defecto
        AndroidView(factory = { mapView }) { view ->
            view.getMapAsync { map ->
                googleMap = map
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isMapToolbarEnabled = false
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(colegio, 16f))
                map.clear()
                map.addMarker(
                    MarkerOptions()
                        .position(colegio)
                        .title(stringResource(R.string.ubicacion_colegio))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_school))
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    FirebaseAnalytics.getInstance(context).logEvent("ubicacion_click_centrar", null)
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        centerOnMyLocation(fused, googleMap, onError = { msg -> errorMessage = msg })
                    } else {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text(stringResource(R.string.centrar_en_mi)) }

            Button(
                onClick = {
                    FirebaseAnalytics.getInstance(context).logEvent("ubicacion_click_ir_colegio", null)
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${colegio.latitude},${colegio.longitude}&travelmode=driving")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                    if (intent.resolveActivity(context.packageManager) == null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } else {
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text(stringResource(R.string.ir_al_colegio)) }
        }

        errorMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun centerOnMyLocation(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    googleMap: GoogleMap?,
    onError: (String) -> Unit
) {
    try {
        fused.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && googleMap != null) {
                    val me = LatLng(location.latitude, location.longitude)
                    try { googleMap.isMyLocationEnabled = true } catch (_: SecurityException) {}
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
                } else {
                    onError(stringResourceSafe(R.string.ubicacion_no_disponible))
                }
            }
            .addOnFailureListener {
                onError(stringResourceSafe(R.string.ubicacion_no_disponible))
            }
    } catch (_: SecurityException) {
        onError(stringResourceSafe(R.string.activa_ubicacion_para_ruta))
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = try { stringResource(id) } catch (_: Exception) { "" }
