package com.example.appcolegios.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import java.util.Calendar

@Suppress("unused")
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReset: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val authState by authViewModel.authState.collectAsState()

    val emailValid = remember(email) { android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() }
    val passwordValid = remember(password) { password.length >= 6 }
    val formValid = emailValid && passwordValid

    // Visual parameters
    val primaryBlue = Color(0xFF2D8CF0)
    val secondaryTone = Color(0xFF1565C0)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Card container centrado con más espacio y radios suaves
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .wrapContentHeight()
                        .shadow(10.dp, MaterialTheme.shapes.medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    // Alineo el contenido interno a la izquierda para jerarquía visual,
                    // pero la tarjeta en sí está centrada en pantalla.
                    Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.Start) {
                        // Logo integrado con el nombre de la institución
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo",
                                modifier = Modifier.size(84.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Nombre de la Institución",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.login_subtitle_quick),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(26.dp))

                        // Título con mayor jerarquía
                        Text(
                            text = stringResource(R.string.login),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        // Email field con etiqueta fija encima
                        Text(text = stringResource(R.string.email), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            isError = email.isNotBlank() && !emailValid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                focusedLabelColor = primaryBlue,
                                cursorColor = primaryBlue
                            ),
                            placeholder = { Text(text = "correo@ejemplo.com", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        if (email.isNotBlank() && !emailValid) {
                            Text(text = stringResource(R.string.invalid_email), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }

                        Spacer(Modifier.height(18.dp))

                        // Password field con etiqueta fija encima y icono sutil
                        Text(text = stringResource(R.string.password), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            isError = password.isNotBlank() && !passwordValid,
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    val iconTint = if (passwordVisible) primaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                        tint = iconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                focusedLabelColor = primaryBlue,
                                cursorColor = primaryBlue
                            ),
                            placeholder = { Text(text = "••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        if (password.isNotBlank() && !passwordValid) {
                            Text(text = stringResource(R.string.password_too_short), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }

                        Spacer(Modifier.height(24.dp))

                        // Botón principal más prominente con animación de presión
                        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "login_btn_scale")

                        Button(
                            onClick = { authViewModel.login(email.trim(), password) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                            enabled = formValid && authState !is AuthState.Loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue,
                                contentColor = Color.White,
                                disabledContainerColor = primaryBlue.copy(alpha = 0.45f),
                                disabledContentColor = Color.White.copy(alpha = 0.8f)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            interactionSource = interaction
                        ) {
                            Text(text = stringResource(R.string.login), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onNavigateToReset, colors = ButtonDefaults.textButtonColors(contentColor = secondaryTone)) {
                                Text(text = stringResource(R.string.reset_password), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = onNavigateToRegister, colors = ButtonDefaults.textButtonColors(contentColor = secondaryTone)) {
                                Text(text = stringResource(R.string.register), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Mensajes de estado de autenticación
                        when (authState) {
                            is AuthState.Loading -> {
                                Spacer(Modifier.height(12.dp))
                                CircularProgressIndicator(color = primaryBlue, modifier = Modifier.size(28.dp))
                            }
                            is AuthState.Error -> {
                                Spacer(Modifier.height(12.dp))
                                Text(text = (authState as AuthState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            else -> Unit
                        }

                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Pie de página pequeño para confianza
                Text(text = "© ${Calendar.getInstance().get(Calendar.YEAR)} Nombre de la Institución", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }
}
