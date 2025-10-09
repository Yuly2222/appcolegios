package com.example.appcolegios.perfil

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.appcolegios.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicInfoScreen() {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.notes),
        stringResource(R.string.attendance),
        stringResource(R.string.tasks)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.my_academic_information)) })
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
                0 -> Text("Contenido de Notas")
                1 -> Text("Contenido de Asistencia")
                2 -> Text("Contenido de Tareas")
            }
        }
    }
}
