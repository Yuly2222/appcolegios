package com.example.appcolegios.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Student
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, profileViewModel: ProfileViewModel = viewModel()) {
    val studentResult by profileViewModel.student.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.profile)) })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            studentResult?.let { result ->
                if (result.isSuccess) {
                    result.getOrNull()?.let { student ->
                        ProfileContent(student = student, navController = navController)
                    } ?: Text(stringResource(R.string.no_student_data))
                } else {
                    Text(
                        text = stringResource(R.string.error_cargando_datos),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } ?: CircularProgressIndicator()
        }
    }
}

@Composable
fun ProfileContent(student: Student, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = student.avatarUrl,
            contentDescription = stringResource(R.string.foto_perfil_cd),
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = student.nombre, style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.curso_label) + ": ${student.curso} - ${student.grupo}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        ProfileInfoRow(stringResource(R.string.correo_institucional_label), student.correoInstitucional)
        ProfileInfoRow(stringResource(R.string.numero_lista_label), student.numeroLista.toString())
        ProfileInfoRow(stringResource(R.string.eps_label), student.eps)
        ProfileInfoRow(stringResource(R.string.estado_matricula_label), student.estadoMatricula)

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { navController.navigate("academics") },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(R.string.my_academic_information))
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
