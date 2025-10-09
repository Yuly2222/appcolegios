package com.example.appcolegios.academico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.AttendanceEntry
import com.example.appcolegios.data.model.AttendanceStatus
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(attendanceViewModel: AttendanceViewModel = viewModel()) {
    val uiState by attendanceViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.attendance)) })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            MonthlySummary(entries = uiState.entries)
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
                    CalendarView(entries = uiState.entries)
                }
            }
        }
    }
}

@Composable
fun CalendarView(entries: Map<Date, AttendanceEntry>) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }

    Column(modifier = Modifier.padding(16.dp)) {
        MonthHeader(
            calendar = currentMonth,
            onPreviousMonth = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            },
            onNextMonth = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            }
        )
        DaysOfWeekHeader()
        MonthGrid(calendar = currentMonth, entries = entries)
    }
}

@Composable
fun MonthHeader(calendar: Calendar, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.mes_anterior))
        }
        Text(
            text = monthFormat.format(calendar.time).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.mes_siguiente))
        }
    }
}

@Composable
fun DaysOfWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        val days = stringArrayResource(R.array.weekdays_short_full)
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun MonthlySummary(entries: Map<Date, AttendanceEntry>) {
    if (entries.isEmpty()) return
    val present = entries.values.count { it.estado == AttendanceStatus.PRESENTE }
    val porcentaje = (present * 100) / entries.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.asistencia_mes_resumen, porcentaje), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MonthGrid(calendar: Calendar, entries: Map<Date, AttendanceEntry>) {
    val monthCal = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDayIndex = monthCal.get(Calendar.DAY_OF_WEEK) - 1 // 0..6
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Build list with leading nulls then days
    val cells = MutableList(firstDayIndex) { null as Int? } + (1..daysInMonth).map { it }
    // Pad to complete last week
    val padded = if (cells.size % 7 == 0) cells else cells + List(7 - (cells.size % 7)) { null }
    val weeks = padded.chunked(7)

    Column {
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dayNum ->
                    val attendanceStatus = dayNum?.let { d ->
                        val dayCal = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
                        val entry = entries.values.firstOrNull { areDatesEqual(it.fecha, dayCal.time) }
                        entry?.estado
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNum != null) {
                            DayCell(day = dayNum, status = attendanceStatus, onClick = { /* detalle futuro */ })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun DayCell(day: Int, status: AttendanceStatus?, onClick: (() -> Unit)? = null) {
    val cd = when (status) {
        AttendanceStatus.PRESENTE -> stringResource(R.string.asistencia_presente_cd)
        AttendanceStatus.AUSENTE -> stringResource(R.string.asistencia_ausente_cd)
        AttendanceStatus.TARDE -> stringResource(R.string.asistencia_tarde_cd)
        null -> ""
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Text(text = day.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = when (status) {
                AttendanceStatus.PRESENTE -> "✅"
                AttendanceStatus.AUSENTE -> "❌"
                AttendanceStatus.TARDE -> "⚠️"
                null -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { contentDescription = cd }
        )
    }
}

private fun areDatesEqual(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
           cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
}
