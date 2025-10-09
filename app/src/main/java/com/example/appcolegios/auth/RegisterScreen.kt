@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.appcolegios.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Role
import androidx.compose.material3.MenuAnchorType

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(Role.ESTUDIANTE) }
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                navController.navigate("login") { popUpTo("register") { inclusive = true } }
                // establecer mensaje en el BackStackEntry de 'login'
                navController.getBackStackEntry("login").savedStateHandle.set("msg", "account_created")
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = document,
                onValueChange = { document = it },
                label = { Text(stringResource(R.string.document)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.phone)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; showError = null },
                label = { Text(stringResource(R.string.confirm_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stringResource(id = when(selectedRole){
                        Role.ESTUDIANTE -> R.string.role_student
                        Role.PADRE -> R.string.role_parent
                        Role.DOCENTE -> R.string.role_teacher
                    }),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_role)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(type = MenuAnchorType.Default, enabled = true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Role.entries.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(stringResource(id = when(role){
                                Role.ESTUDIANTE -> R.string.role_student
                                Role.PADRE -> R.string.role_parent
                                Role.DOCENTE -> R.string.role_teacher
                            })) },
                            onClick = {
                                selectedRole = role
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { authViewModel.register(name, email, document, phone, password, confirmPassword, selectedRole) },
                enabled = authState !is AuthState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.create_account))
                }
            }

            if (showError != null) {
                Spacer(Modifier.height(12.dp))
                Text(showError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
