package com.example.appcolegios.ubicacion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.analytics.FirebaseAnalytics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.appcolegios.util.rememberMapViewWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import java.security.MessageDigest
import android.os.Build

import androidx.core.net.toUri

@Composable
fun UbicacionScreen() {
    val context = LocalContext.current

    // Coordenadas por defecto: Centro Comercial Centro Chía (fallback)
    // Asumimos coordenadas aproximadas; actualízalas si tienes las oficiales.
    val centroChia = remember { LatLng(4.8933, -73.9978) }

    // Strings extraídas en scope composable
    val ubicacionTitle = stringResource(R.string.ubicacion_colegio)
    val activaMsg = stringResource(R.string.activa_ubicacion_para_ruta)
    val noDisponibleMsg = stringResource(R.string.ubicacion_no_disponible)

    val googleMapsKey = try { context.getString(R.string.google_maps_key) } catch (_: Exception) { "" }
    val hasMapsKey = googleMapsKey.isNotBlank() && !googleMapsKey.contains("REEMPLAZA_CON_TU_API_KEY")

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
            // Si no concede permisos, vamos por defecto al Centro Chía
            errorMessage = activaMsg
            openMapsToLocation(context, centroChia)
        } else {
            centerOnMyLocation(context, fused, googleMap, centroChia) { errorMessage = noDisponibleMsg }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Diagnostic panel (debug only): show package name and SHA-1 to help configure Maps API key
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            val pkg = context.packageName
            val sha1 = remember { getSigningSha1(context) ?: "<no-signature>" }
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Diagnostics (debug)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Package: $pkg", style = MaterialTheme.typography.bodySmall)
                    Text("SHA1: $sha1", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            // copy to clipboard
                            try {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("sha1", sha1)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "SHA-1 copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            } catch (ex: Exception) {
                                Toast.makeText(context, "No se pudo copiar: ${ex.message}", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Copiar SHA1") }
                        Button(onClick = {
                            // open console docs/credentials
                            val url = "https://console.cloud.google.com/apis/credentials"
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) {}
                        }) { Text("Abrir Google Cloud") }
                     }
                    Spacer(Modifier.height(6.dp))
                    Text("Revisa que tu API Key tenga restricción de Android con este paquete y SHA-1.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (!hasMapsKey) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("API de Google Maps no configurada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text("Coloca tu API key en strings.xml (google_maps_key) para habilitar el mapa.")
                }
            }
        } else {
            // Mapa en modo lectura por defecto
            AndroidView(factory = { mapView }) { view ->
                view.getMapAsync { map ->
                    googleMap = map
                    map.uiSettings.isZoomControlsEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = false
                    map.uiSettings.isMapToolbarEnabled = false
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(centroChia, 16f))
                    map.clear()
                    map.addMarker(
                        MarkerOptions()
                            .position(centroChia)
                            .title(ubicacionTitle)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_school))
                    )
                }
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
                        centerOnMyLocation(context, fused, googleMap, centroChia) { errorMessage = noDisponibleMsg }
                    } else {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text(ubicacionTitle) }

            Button(
                onClick = {
                    FirebaseAnalytics.getInstance(context).logEvent("ubicacion_click_ir_colegio", null)
                    // Ir directo al Centro Chía
                    openMapsToLocation(context, centroChia)
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

private fun openMapsToLocation(context: Context, destination: LatLng) {
    val uriStr = "https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}&travelmode=driving"
    val uri = uriStr.toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    val pm = context.packageManager
    val target = intent.resolveActivity(pm)
    if (target != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun centerOnMyLocation(
    context: Context,
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    googleMap: GoogleMap?,
    fallback: LatLng,
    onError: () -> Unit
) {
    try {
        fused.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && googleMap != null) {
                    val me = LatLng(location.latitude, location.longitude)
                    try { googleMap.isMyLocationEnabled = true } catch (_: SecurityException) {}
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f))
                } else {
                    // Si no hay mapa (p. ej. API key no configurada) o no hay ubicación, abrir Maps al fallback
                    if (googleMap != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(fallback, 16f))
                    } else {
                        openMapsToLocation(context, fallback)
                    }
                    onError()
                }
            }
            .addOnFailureListener {
                // Fallback: abrir mapas a la ubicación por defecto
                openMapsToLocation(context, fallback)
                onError()
            }
    } catch (_: SecurityException) {
        openMapsToLocation(context, fallback)
        onError()
    }
}

fun getSigningSha1(context: Context): String? {
    try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val sigs = pkgInfo.signingInfo?.apkContentsSigners ?: pkgInfo.signingInfo?.signingCertificateHistory
            val arr = sigs ?: emptyArray()
            for (signature in arr) {
                val md: MessageDigest = MessageDigest.getInstance("SHA1")
                val digest = md.digest(signature.toByteArray())
                val sb = StringBuilder()
                for (i in digest.indices) {
                    if (i > 0) sb.append(':')
                    sb.append(String.format("%02X", digest[i]))
                }
                return sb.toString()
            }
        } else {
            @Suppress("DEPRECATION")
            val pkgInfo: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val arr = pkgInfo.signatures ?: emptyArray()
            for (signature in arr) {
                val md: MessageDigest = MessageDigest.getInstance("SHA1")
                val digest = md.digest(signature.toByteArray())
                val sb = StringBuilder()
                for (i in digest.indices) {
                    if (i > 0) sb.append(':')
                    sb.append(String.format("%02X", digest[i]))
                }
                return sb.toString()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
