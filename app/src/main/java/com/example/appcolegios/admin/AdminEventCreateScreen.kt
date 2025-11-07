package com.example.appcolegios.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
fun AdminEventCreateScreen(navController: NavController? = null) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // Fecha/hora del evento (por defecto ahora)
    var dateCal by remember { mutableStateOf(Calendar.getInstance()) }
    var isLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun openDatePicker() {
        val c = dateCal
        android.app.DatePickerDialog(context, { _, y, m, d ->
            c.set(Calendar.YEAR, y)
            c.set(Calendar.MONTH, m)
            c.set(Calendar.DAY_OF_MONTH, d)
            dateCal = c
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun openTimePicker() {
        val c = dateCal
        android.app.TimePickerDialog(context, { _, hour, minute ->
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0)
            dateCal = c
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Crear Evento / Notificación", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Cuerpo") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openDatePicker() }) { Text("Seleccionar fecha") }
            Button(onClick = { openTimePicker() }) { Text("Seleccionar hora") }
            Text("${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(dateCal.time)}")
        }

        Button(onClick = {
            scope.launch {
                isLoading = true
                try {
                    val eventId = db.collection("events").document().id
                    val eventData = hashMapOf<String, Any?>(
                        "id" to eventId,
                        "title" to title,
                        "description" to body,
                        "date" to Timestamp(dateCal.time),
                        "type" to "EVENTO",
                        "createdAt" to Timestamp.now(),
                        "senderName" to "ADMIN"
                    )
                    // Guardar en top-level events
                    db.collection("events").document(eventId).set(eventData).await()

                    // Crear also global notification doc for audit/feeds
                    val notifGlobal = hashMapOf(
                        "titulo" to title,
                        "cuerpo" to body,
                        "remitente" to "ADMIN",
                        "senderName" to "ADMIN",
                        "fechaHora" to Timestamp.now(),
                        "leida" to false,
                        "tipo" to "evento",
                        "relatedId" to eventId
                    )
                    db.collection("notifications").add(notifGlobal).await()

                    // Replicar notificaciones a cada usuario (users/{uid}/notifications)
                    val usersSnap = db.collection("users").get().await()
                    for (u in usersSnap.documents) {
                        try {
                            val localNotif = notifGlobal.toMutableMap()
                            // para compatibilidad añadir 'type' también
                            localNotif["type"] = "event"
                            db.collection("users").document(u.id).collection("notifications").add(localNotif).await()
                        } catch (_: Exception) {
                        }
                    }

                    status = "Evento creado"
                    navController?.popBackStack()
                } catch (e: Exception) {
                    status = e.message
                } finally {
                    isLoading = false
                }
            }
        }, enabled = !isLoading) {
            Text("Crear y enviar")
        }
        if (status != null) Text(status!!)
    }
}
