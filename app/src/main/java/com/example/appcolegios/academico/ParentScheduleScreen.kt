package com.example.appcolegios.academico

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.perfil.ProfileViewModel
import com.example.appcolegios.data.UserPreferencesRepository
import androidx.compose.ui.platform.LocalContext

@Composable
fun ParentScheduleScreen(scheduleViewModel: ScheduleViewModel = viewModel()) {
    val context = LocalContext.current
    val userPrefs = UserPreferencesRepository(context)
    val userData by userPrefs.userData.collectAsState(initial = com.example.appcolegios.data.UserData(null, null, null))
    val role = userData.role ?: ""

    val profileVm: ProfileViewModel = viewModel()
    val children by profileVm.children.collectAsState(initial = emptyList())
    val selectedIndexState by profileVm.selectedChildIndex.collectAsState(initial = 0)
    val selectedIndex = selectedIndexState ?: 0

    // Si el usuario es PADRE y hay un hijo seleccionado, cargar su schedule
    LaunchedEffect(children, selectedIndex, role) {
        if ((role.equals("PADRE", ignoreCase = true) || role.equals("PARENT", ignoreCase = true)) && children.isNotEmpty()) {
            val childId = children.getOrNull(selectedIndex)?.id
            scheduleViewModel.loadScheduleFor(childId)
        } else {
            // cargar schedule del usuario autenticado
            scheduleViewModel.loadScheduleFor(null)
        }
    }

    // Reusar la UI existente que lee el uiState del ScheduleViewModel
    ScheduleScreen(scheduleViewModel = scheduleViewModel)
}

