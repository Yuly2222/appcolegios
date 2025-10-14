package com.example.appcolegios.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReset: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    // Precargar datos del profesor para pruebas
    var email by remember { mutableStateOf("hermanitos605@gmail.com") }
    var password by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()

    val emailValid = remember(email) {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    val passwordValid = remember(password) { password.length >= 6 }
    val formValid = emailValid && passwordValid

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo institucional
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo Institucional",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 24.dp)
            )

            Text(
                stringResource(R.string.welcome_friendly),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_subtitle_quick),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Card blanco para contener los campos
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.email)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = email.isNotBlank() && !emailValid,
                        supportingText = {
                            if (email.isNotBlank() && !emailValid) {
                                Text(stringResource(R.string.invalid_email))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = password.isNotBlank() && !passwordValid,
                        supportingText = {
                            if (password.isNotBlank() && !passwordValid) {
                                Text(stringResource(R.string.password_too_short))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (authState) {
                is AuthState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is AuthState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = (authState as AuthState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is AuthState.Authenticated -> { /* Navega abajo */ }
                else -> {}
            }

            // Botón principal con microanimación de presión
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "login_btn_scale")
            Button(
                onClick = { authViewModel.login(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                enabled = formValid && authState !is AuthState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF1565C0), // Azul oscuro
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = androidx.compose.ui.graphics.Color(0xFF1565C0).copy(alpha = 0.4f),
                    disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                ),
                shape = MaterialTheme.shapes.small,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                interactionSource = interaction
            ) {
                Text(stringResource(R.string.login), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CTA a registro, tono empático
            TextButton(
                onClick = onNavigateToRegister,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color(0xFF1565C0) // Azul oscuro
                )
            ) {
                Text(
                    text = stringResource(R.string.register),
                    color = androidx.compose.ui.graphics.Color(0xFF1565C0),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // CTA a restablecer contraseña
            TextButton(
                onClick = onNavigateToReset,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color(0xFF1565C0) // Azul oscuro
                )
            ) {
                Text(
                    text = stringResource(R.string.reset_password),
                    color = androidx.compose.ui.graphics.Color(0xFF1565C0),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }
}
