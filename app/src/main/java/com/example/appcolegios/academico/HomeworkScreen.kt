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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Homework
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen(homeworkViewModel: HomeworkViewModel = viewModel()) {
    val uiState by homeworkViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tasks)) })
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
                    Text(stringResource(R.string.error_label) + ": " + (uiState.error ?: ""))
                }
            }
            uiState.homeworks.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_tasks_for_today))
                }
            }
            else -> {
                HomeworkList(
                    homeworks = uiState.homeworks,
                    onToggle = { id, completed -> homeworkViewModel.toggleHomeworkStatus(id, completed) },
                    modifier = Modifier.padding(paddingValues),
                    onClick = { /* navegar a detalle futuro */ }
                )
            }
        }
    }
}

@Composable
fun HomeworkList(homeworks: List<Homework>, onToggle: (String, Boolean) -> Unit, modifier: Modifier = Modifier, onClick: (Homework) -> Unit) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(homeworks) { homework ->
            HomeworkItem(homework = homework, onToggle = onToggle, onClick = { onClick(homework) })
        }
    }
}

@Composable
fun HomeworkItem(homework: Homework, onToggle: (String, Boolean) -> Unit, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = homework.completada,
                onCheckedChange = { onToggle(homework.id, it) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = homework.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (homework.completada) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = homework.materia,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { homework.progreso }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Entrega: ${dateFormat.format(homework.deadline)}  ${(homework.progreso * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = dateFormat.format(homework.deadline),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
