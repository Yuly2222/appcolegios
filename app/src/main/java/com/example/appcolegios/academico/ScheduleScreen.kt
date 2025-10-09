package com.example.appcolegios.academico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.ClassSession

@Composable
fun ScheduleScreen(scheduleViewModel: ScheduleViewModel = viewModel()) {
    val uiState by scheduleViewModel.uiState.collectAsState()
    var selectedDay by remember { mutableStateOf(1) } // Monday

    Column(modifier = Modifier.fillMaxSize()) {
        DaySelector(selectedDay = selectedDay, onDaySelected = { selectedDay = it })
        HorizontalDivider()
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.error_generic) + ": " + (uiState.error ?: ""))
                }
            }
            else -> {
                val daySchedule = uiState.schedule[selectedDay] ?: emptyList()
                if (daySchedule.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sin clases")
                    }
                } else {
                    ScheduleList(schedule = daySchedule)
                }
            }
        }
    }
}

@Composable
fun DaySelector(selectedDay: Int, onDaySelected: (Int) -> Unit) {
    val days = stringArrayResource(R.array.weekdays_short)
    ScrollableTabRow(
        selectedTabIndex = selectedDay - 1,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 0.dp
    ) {
        days.forEachIndexed { index, day ->
            Tab(
                selected = selectedDay == index + 1,
                onClick = { onDaySelected(index + 1) },
                text = { Text(day) }
            )
        }
    }
}

@Composable
fun ScheduleList(schedule: List<ClassSession>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(schedule) { session ->
            ScheduleItem(session = session)
        }
    }
}

@Composable
fun ScheduleItem(session: ClassSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = session.teacher, style = MaterialTheme.typography.bodyMedium)
                Text(text = "Aula: ${session.classroom}", style = MaterialTheme.typography.bodySmall)
            }
            Text(text = "${session.startTime} - ${session.endTime}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
