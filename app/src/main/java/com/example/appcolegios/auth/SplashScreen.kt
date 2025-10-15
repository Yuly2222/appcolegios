package com.example.appcolegios.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.appcolegios.R
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.UserData
import com.example.appcolegios.util.ConnectionStatus
import com.example.appcolegios.util.connectivityState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val userPreferencesRepository = UserPreferencesRepository(context)
    val userData by userPreferencesRepository.userData.collectAsState(initial = UserData(null, null, null))
    var showError by remember { mutableStateOf(false) }
    val connection by connectivityState()
    val isConnected = connection === ConnectionStatus.Available
    val scope = rememberCoroutineScope()

    // Lanzar verificación desde una corutina; la lógica heavy está en la función suspend top-level
    fun verifySession() {
        scope.launch {
            performSessionVerification(context, navController, userPreferencesRepository, userData, isConnected) { showError = it }
        }
    }

    LaunchedEffect(key1 = isConnected) {
        if (isConnected) {
            verifySession()
        } else {
            showError = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (showError) {
                Text(
                    text = stringResource(R.string.network_error_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { verifySession() }) {
                    Text(text = stringResource(R.string.retry))
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

// Mover la lógica de verificación a una función suspend top-level para evitar problemas con
// funciones locales dentro de un @Composable que podrían capturar llamadas composables.
suspend fun performSessionVerification(
    context: Context,
    navController: NavController,
    userPreferencesRepository: UserPreferencesRepository,
    userData: UserData?,
    isConnected: Boolean,
    onShowError: (Boolean) -> Unit
) {
    onShowError(false)
    // Mantener la pausa simulada
    delay(1500)
    if (!isConnected) {
        onShowError(true)
        return
    }

    val firebaseUser = FirebaseAuth.getInstance().currentUser
    if (userData?.userId != null) {
        navController.navigate("home") { popUpTo("splash") { inclusive = true } }
        return
    }

    if (firebaseUser != null) {
        try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(firebaseUser.uid).get().await()
            val roleStr = snap.getString("role")
            val name = snap.getString("name") ?: ""
            userPreferencesRepository.updateUserData(firebaseUser.uid, roleStr, name)
        } catch (_: Exception) {
            // ignore, defaults later
        }
        navController.navigate("home") { popUpTo("splash") { inclusive = true } }
        return
    }

    // No hay sesión: abrir LoginActivity (XML) si hay Activity disponible, si no usar navController
    val activity = (context as? Activity)
    activity?.let {
        it.startActivity(Intent(it, LoginActivity::class.java))
        it.finish()
    } ?: run {
        navController.popBackStack(0, inclusive = true)
    }
}
