package com.example.appcolegios.academico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appcolegios.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.attendance),
        stringResource(R.string.schedule),
        stringResource(R.string.events)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.calendar)) })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(text = title) }
                    )
                }
            }
            when (tabIndex) {
                0 -> AttendanceScreen()
                1 -> ScheduleScreen()
                2 -> EventsScreen()
            }
        }
    }
}
