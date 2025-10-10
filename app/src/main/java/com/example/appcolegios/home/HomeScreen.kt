package com.example.appcolegios.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.appcolegios.R
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import com.example.appcolegios.navigation.AppRoutes

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }

    val vm: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(userPrefs) as T
        }
    })

    val ui by vm.ui.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            ui.loading -> LoadingState()
            ui.error != null -> ErrorState(ui.error!!)
            ui.role == null && (ui.name?.isBlank() != false) -> EmptyState()
            else -> HomeContent(ui = ui, onNavigate = { route -> navController.navigate(route) })
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.network_error).replace("red", "cargando"),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.empty_home),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun HomeContent(ui: HomeUiState, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Saludo
        Text(
            text = if (!ui.name.isNullOrBlank()) stringResource(R.string.greeting, ui.name!!) else stringResource(R.string.home),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))

        // Badges rápidos
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(
                onClick = { onNavigate(AppRoutes.Notifications.route) },
                label = { Text(stringResource(R.string.unread_notifications, ui.unreadNotifications)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
            AssistChip(
                onClick = { onNavigate(AppRoutes.Messages.route) },
                label = { Text(stringResource(R.string.unread_messages, ui.unreadMessages)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Banner de pagos a considerar (solo estudiantes/padres)
        if (ui.pagosEnConsideracion && ui.role != Role.DOCENTE) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.payments_to_consider),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = { onNavigate(AppRoutes.Payments.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.payments))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Secciones por rol
        SectionHeader(stringResource(R.string.my_academic_information))
        AcademicSection(onNavigate)

        Spacer(Modifier.height(16.dp))

        // Accesos generales
        val tiles = buildList {
            add(QuickTile(stringResource(R.string.transporte)) { onNavigate(AppRoutes.Transport.route) })
            add(QuickTile(stringResource(R.string.calendar)) { onNavigate(AppRoutes.Calendar.route) })
            if (ui.role != Role.DOCENTE) {
                add(QuickTile(stringResource(R.string.payments)) { onNavigate(AppRoutes.Payments.route) })
            }
            add(QuickTile(stringResource(R.string.profile)) { onNavigate(AppRoutes.Profile.route) })
        }
        QuickGrid(tiles)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(8.dp))
}

private data class QuickTile(val title: String, val onClick: () -> Unit)

@Composable
private fun QuickGrid(items: List<QuickTile>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { tile ->
            ElevatedCard(onClick = tile.onClick) {
                Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(tile.title)
                }
            }
        }
    }
}

@Composable
private fun AcademicSection(onNavigate: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Notes.route) }) {
            Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notes))
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Attendance.route) }) {
            Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.attendance))
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Tasks.route) }) {
            Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tasks))
            }
        }
    }
}
