package com.example.appcolegios.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R

@Composable
fun ProfileScreen(profileViewModel: ProfileViewModel = viewModel()) {
    val studentResult by profileViewModel.student.collectAsState(initial = null)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val result = studentResult) {
            null -> { CircularProgressIndicator() }
            else -> {
                result.onSuccess { student ->
                    if (student != null) {
                        Text(text = student.nombre, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(text = stringResource(R.string.curso_label) + ": " + student.curso)
                        Text(text = stringResource(R.string.select_group).replace("Selecciona ", "") + ": " + student.grupo)
                        Text(text = stringResource(R.string.promedio_global) + ": " + student.promedio)
                    } else {
                        Text(text = stringResource(R.string.no_student_data))
                    }
                }.onFailure { e ->
                    Text(text = stringResource(R.string.error_label) + ": " + (e.message ?: ""))
                }
            }
        }
    }
}
