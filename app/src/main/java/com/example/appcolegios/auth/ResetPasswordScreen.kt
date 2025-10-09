package com.example.appcolegios.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.appcolegios.R
import androidx.compose.ui.platform.LocalContext

@Composable
fun ResetPasswordScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()
    var showError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                val msg = ctx.getString(R.string.recovery_email_sent)
                snackbarHostState.showSnackbar(msg)
                navController.navigate("login") { popUpTo("reset") { inclusive = true } }
                authViewModel.clearState()
            }
            is AuthState.Error -> showError = (authState as AuthState.Error).message
            else -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; showError = null },
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { authViewModel.resetPassword(email) },
                enabled = email.isNotBlank() && authState !is AuthState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.send_recovery_email))
                }
            }
            if (showError != null) {
                Spacer(Modifier.height(12.dp))
                Text(showError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { navController.navigate("login") }) {
                Text(stringResource(R.string.back_to_login))
            }
        }
    }
}
