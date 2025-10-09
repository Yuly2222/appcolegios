package com.example.appcolegios.academico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Grade
import java.text.DecimalFormat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(gradesViewModel: GradesViewModel = viewModel()) {
    val uiState by gradesViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.notes)) })
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic) + ": " + (uiState.error ?: ""))
                }
            }
            else -> {
                GradesContent(
                    grades = uiState.grades,
                    overallAverage = uiState.overallAverage,
                    modifier = Modifier.padding(paddingValues),
                    onGradeClick = { grade -> /* navegar a detalle implementado en navegación */ }
                )
            }
        }
    }
}

@Composable
fun GradesContent(grades: List<Grade>, overallAverage: Double, modifier: Modifier = Modifier, onGradeClick: (Grade) -> Unit) {
    val decimalFormat = DecimalFormat("#.##")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Promedio global", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = decimalFormat.format(overallAverage),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
        }
        items(grades) { grade ->
            GradeItem(grade = grade, decimalFormat = decimalFormat, onClick = { onGradeClick(grade) })
            HorizontalDivider()
        }
    }
}

@Composable
fun GradeItem(grade: Grade, decimalFormat: DecimalFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = grade.materia, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = decimalFormat.format(grade.calificacion),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
