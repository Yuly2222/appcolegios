package com.example.appcolegios.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    createOnly: Boolean = false // si true: crear solo documento en Firestore sin tocar Auth
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("ESTUDIANTE") }
    var expanded by remember { mutableStateOf(false) }
    val authState by authViewModel.authState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                "Crear Cuenta",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Card blanco para contener los campos
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Nombre Completo") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo Electrónico") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Solo pedir contraseña cuando NO es createOnly (admin crea cuenta sin auth)
                    if (!createOnly) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Selector de rol
                    val roles = if (createOnly) listOf("ESTUDIANTE", "PADRE", "DOCENTE", "ADMIN") else listOf("ESTUDIANTE", "PADRE", "DOCENTE")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = role,
                            onValueChange = {},
                            label = { Text("Rol") },
                            readOnly = true,
                            trailingIcon = {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null,
                                    modifier = Modifier.clickable { expanded = !expanded })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            roles.forEach { r ->
                                DropdownMenuItem(text = { Text(r) }, onClick = {
                                    role = r
                                    expanded = false
                                })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Estados y mensajes
            var status by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(false) }

            when (authState) {
                is AuthState.Loading -> {
                    if (!createOnly) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is AuthState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
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
                is AuthState.Authenticated -> {
                    // No hacemos nada aquí cuando createOnly == true
                }
                else -> {}
            }

            // Botón principal - azul oscuro
            Button(
                onClick = {
                    if (createOnly) {
                        // Crear documento en Firestore sin tocar Auth
                        scope.launch {
                            isLoading = true
                            status = null
                            try {
                                val db = FirebaseFirestore.getInstance()
                                val data = hashMapOf(
                                    "email" to email,
                                    "name" to displayName,
                                    "role" to role,
                                    "createdAt" to com.google.firebase.Timestamp.now()
                                )
                                val coll = when (role.uppercase()) {
                                    "PADRE" -> "parents"
                                    "DOCENTE" -> "teachers"
                                    "ADMIN" -> "admins"
                                    else -> "students"
                                }
                                db.collection(coll).add(data).await()
                                // Informar éxito
                                Toast.makeText(context, "Usuario creado correctamente", Toast.LENGTH_SHORT).show()
                                onRegisterSuccess()
                            } catch (e: Exception) {
                                status = "Error al crear usuario: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        authViewModel.register(email, password, displayName, role)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !(authState is AuthState.Loading) && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.small,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text("Registrarse", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Botón secundario - azul claro
            TextButton(
                onClick = onNavigateToLogin,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    "¿Ya tienes una cuenta? Inicia Sesión",
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }

            // Mostrar status si existe
            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Text(status!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Solo navegar automáticamente si no estamos en modo createOnly
    LaunchedEffect(authState) {
        if (!createOnly && authState is AuthState.Authenticated) {
            onRegisterSuccess()
        }
    }
}
