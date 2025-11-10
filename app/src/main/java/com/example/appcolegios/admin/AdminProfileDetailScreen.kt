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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun AdminProfileDetailScreen(navController: NavController? = null, userId: String) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        isLoading = true
        try {
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) {
                displayName = doc.getString("displayName") ?: doc.getString("name") ?: ""
                email = doc.getString("email") ?: ""
                role = doc.getString("role") ?: ""
            } else {
                // intentar en students/teachers
                val s = db.collection("students").document(userId).get().await()
                if (s.exists()) {
                    displayName = s.getString("nombre") ?: ""
                    email = s.getString("email") ?: ""
                    role = s.getString("role") ?: "ESTUDIANTE"
                }
            }
        } catch (e: Exception) {
            status = e.message
        } finally {
            isLoading = false
        }
    }

    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detalle de perfil", style = MaterialTheme.typography.headlineSmall)
        if (isLoading) CircularProgressIndicator()
        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    try {
                        // actualizar collection users
                        val map: Map<String, Any?> = mapOf("displayName" to displayName, "email" to email, "role" to role, "updatedAt" to Timestamp.now())
                        db.collection("users").document(userId).set(map).await()
                        // intentar actualizar en students/teachers si existe (update requiere Map<String, Any?>)
                        try { db.collection("students").document(userId).update(map).await() } catch (_: Exception) {}
                        try { db.collection("teachers").document(userId).update(map).await() } catch (_: Exception) {}
                        status = "Guardado"
                    } catch (e: Exception) {
                        status = e.message
                    }
                }
            }) { Text("Guardar") }

            Button(onClick = { navController?.popBackStack() }) { Text("Volver") }
        }

        if (status != null) Text(status!!)
    }
}
