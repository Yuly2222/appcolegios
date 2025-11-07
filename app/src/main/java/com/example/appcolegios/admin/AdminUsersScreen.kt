package com.example.appcolegios.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment

@Composable
fun AdminUsersScreen(onUserSelected: (String) -> Unit = {}) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val snap = db.collection("users").get().await()
            val list = mutableListOf<Map<String, Any>>()
            for (doc in snap.documents) {
                val map = doc.data?.toMutableMap() ?: mutableMapOf()
                map["id"] = doc.id
                list.add(map)
            }
            users = list
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error") }
        return
    }

    Card(Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(contentPadding = PaddingValues(8.dp)) {
            items(users) { u ->
                val id = u["id"] as? String ?: ""
                val name = (u["displayName"] as? String) ?: (u["name"] as? String) ?: "(sin nombre)"
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserSelected(id) }
                    .padding(vertical = 8.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(u["email"] as? String ?: "", style = MaterialTheme.typography.bodySmall)
                }
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}
