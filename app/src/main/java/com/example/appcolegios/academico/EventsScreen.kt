package com.example.appcolegios.academico

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Event
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventsScreen(eventsViewModel: EventsViewModel = viewModel()) {
    val uiState by eventsViewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_label) + ": " + (uiState.error ?: ""))
            }
        }
        else -> {
            EventsList(events = uiState.events)
        }
    }
}

@Composable
fun EventsList(events: List<Event>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(events) { event ->
            EventItem(event = event)
        }
    }
}

@Composable
fun EventItem(event: Event) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = event.titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormat.format(event.fechaHora),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = event.descripcion, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
