package com.example.appcolegios.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.appcolegios.R
import com.example.appcolegios.data.UserPreferencesRepository

@Composable
fun LoginScreen(navController: NavController) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(UserPreferencesRepository(navController.context))
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()
    var showError by remember { mutableStateOf<String?>(null) }
    val accountCreatedText = stringResource(R.string.account_created)
    // Mostrar confirmación de cuenta creada si existe mensaje
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    // Obtener mensaje con tipo explícito
    val msg: String? = savedStateHandle?.get<String>("msg")
    LaunchedEffect(msg) {
        if (msg == "account_created") {
            snackbarHostState.showSnackbar(accountCreatedText)
            // Eliminar msg de manera segura
            savedStateHandle?.let { it.remove<String>("msg") }
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                navController.navigate("home") { popUpTo("login") { inclusive = true } }
                authViewModel.clearState()
            }
            is AuthState.Error -> showError = (authState as AuthState.Error).message
            else -> Unit
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; showError = null },
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; showError = null },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { authViewModel.login(email, password) },
                enabled = authState !is AuthState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login))
                }
            }
            TextButton(onClick = { navController.navigate("register") }) {
                Text(stringResource(R.string.register))
            }
            TextButton(onClick = { navController.navigate("reset") }) {
                Text(stringResource(R.string.reset_password))
            }

            if (showError != null) {
                Spacer(Modifier.height(12.dp))
                Text(showError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

class AuthViewModelFactory(private val userPreferencesRepository: UserPreferencesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
