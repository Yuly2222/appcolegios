package com.example.appcolegios.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.demo.DemoData

@Composable
fun ProfileScreen(profileViewModel: ProfileViewModel = viewModel()) {
    val studentResult by profileViewModel.student.collectAsState(initial = null)
    val isDemo = DemoData.isDemoUser()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val result = studentResult) {
                null -> {
                    if (isDemo) {
                        StudentCard(student = DemoData.demoStudent())
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                else -> {
                    result.onSuccess { student ->
                        val data = student ?: if (isDemo) DemoData.demoStudent() else null
                        if (data != null) {
                            StudentCard(student = data)
                        } else {
                            Text(
                                text = stringResource(R.string.no_student_data),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }.onFailure { e ->
                        if (isDemo) {
                            StudentCard(student = DemoData.demoStudent())
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                ),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = stringResource(R.string.error_label) + ": " + (e.message ?: ""),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(student: com.example.appcolegios.data.model.Student) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = student.nombre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ProfileInfoRow(
                label = stringResource(R.string.curso_label),
                value = student.curso
            )
            Spacer(Modifier.height(12.dp))

            ProfileInfoRow(
                label = stringResource(R.string.select_group).replace("Selecciona ", ""),
                value = student.grupo
            )
            Spacer(Modifier.height(12.dp))

            ProfileInfoRow(
                label = stringResource(R.string.promedio_global),
                value = student.promedio.toString()
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
