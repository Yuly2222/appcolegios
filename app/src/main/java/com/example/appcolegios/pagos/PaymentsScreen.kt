package com.example.appcolegios.pagos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appcolegios.demo.DemoData
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.perfil.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.collectLatest

// Nuevo modelo simple para pagos
data class Pago(
    val id: String = "",
    val titulo: String = "",
    val monto: Double = 0.0,
    val fecha: Date = Date(),
    val estado: String = "",
    val observaciones: String = "",
    val studentId: String = ""
)

@Composable
fun PaymentsScreen() {
    val context = LocalContext.current
    val isDemo = DemoData.isDemoUser()

    // Obtener usuario y perfil para soporte de padres
    val userPrefs = UserPreferencesRepository(context)
    val userData by userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val profileVm: ProfileViewModel = viewModel()
    val children by profileVm.children.collectAsState()
    val selectedIndexState by profileVm.selectedChildIndex.collectAsState()
    val selectedChildIndex = selectedIndexState ?: 0
    val isParent = (userData.role ?: "").equals("PARENT", ignoreCase = true) || (userData.role ?: "").equals("PADRE", ignoreCase = true)

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    var showSelectChildDialog by remember { mutableStateOf(false) }

    var payments by remember { mutableStateOf<List<Pago>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Observamos el student seleccionado centralmente
    val studentResult by profileVm.student.collectAsState()
    val currentStudent = studentResult?.getOrNull()
    val targetId = if (isParent) currentStudent?.id else auth.currentUser?.uid

    LaunchedEffect(targetId) {
        payments = emptyList()
        if (targetId == null) return@LaunchedEffect
        loading = true
        errorMsg = null
        try {
            val baseQuery = firestore.collection("pagos").whereEqualTo("studentId", targetId).orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
            val query = baseQuery
            val snaps = query.get().await()
            val list = mutableListOf<Pago>()
            for (doc in snaps.documents) {
                val id = doc.id
                val titulo = doc.getString("titulo") ?: doc.getString("title") ?: "Pago"
                val monto = (doc.getDouble("monto") ?: doc.getLong("monto")?.toDouble()) ?: 0.0
                val fecha = doc.getTimestamp("fecha")?.toDate() ?: Date()
                val estado = doc.getString("estado") ?: doc.getString("status") ?: "Pendiente"
                val obs = doc.getString("observaciones") ?: doc.getString("observations") ?: ""
                val studentId = doc.getString("studentId") ?: ""
                list.add(Pago(id, titulo, monto, fecha, estado, obs, studentId))
            }
            payments = list
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            loading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDemo) {
                DemoPaymentsCard()
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Selector de hijo si es padre: usar selección centralizada en ProfileViewModel
            if (isParent) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = children.isNotEmpty()) { showSelectChildDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val name = if (children.isNotEmpty()) children.getOrNull(selectedChildIndex)?.nombre ?: "--" else "--"
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (showSelectChildDialog) {
                    var sel by remember { mutableStateOf(selectedChildIndex) }
                    AlertDialog(onDismissRequest = { showSelectChildDialog = false }, title = { Text("Selecciona estudiante") }, text = {
                        Column {
                            if (children.isEmpty()) Text("No hay hijos asociados") else children.forEachIndexed { idx, ch ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { sel = idx }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = sel == idx, onClick = { sel = idx })
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(ch.nombre, fontWeight = FontWeight.SemiBold)
                                        Text("Curso: ${ch.curso}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }, confirmButton = {
                        TextButton(onClick = {
                            if (children.isNotEmpty()) {
                                // Actualizamos la selección en ProfileViewModel para que todas las pantallas se sincronicen
                                profileVm.selectChildAtIndex(sel)
                            }
                            showSelectChildDialog = false
                        }) { Text("Aceptar") }
                    }, dismissButton = {
                        TextButton(onClick = { showSelectChildDialog = false }) { Text("Cancelar") }
                    })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Información de Pagos",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(12.dp))

            if (loading) {
                androidx.compose.material3.CircularProgressIndicator()
            } else if (errorMsg != null) {
                Text(errorMsg ?: "Error desconocido", color = MaterialTheme.colorScheme.error)
            } else if (payments.isEmpty()) {
                Text("No hay pagos registrados.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(payments) { pago ->
                        PaymentCard(pago)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(pago: Pago) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pago.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(pago.fecha), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(String.format(Locale.getDefault(), "$ %.2f", pago.monto), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pago.estado, style = MaterialTheme.typography.bodyMedium)
                if (pago.observaciones.isNotEmpty()) Text(pago.observaciones, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DemoPaymentsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resumen de pagos (Demo)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pensión Octubre", color = MaterialTheme.colorScheme.onSurface)
                Text("$ 250.000", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pendiente", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
