package com.example.appcolegios.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
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
import com.example.appcolegios.admin.AssignGroupDialog
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import com.example.appcolegios.navigation.AppRoutes
import com.example.appcolegios.demo.DemoData
import com.example.appcolegios.teacher.TeacherHomeScreen
import com.example.appcolegios.parent.ParentHomeScreen
import com.example.appcolegios.student.StudentHomeScreen

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

    when {
        ui.loading -> LoadingState()
        ui.error != null -> ErrorState(ui.error!!)
        ui.role == null && (ui.name?.isBlank() != false) -> EmptyState()
        else -> {
            // Mostrar pantalla específica según el rol
            when (ui.role) {
                Role.ADMIN -> HomeContent(ui = ui, onNavigate = { route -> navController.navigate(route) })
                Role.DOCENTE -> TeacherHomeScreen(navController)
                Role.PADRE -> ParentHomeScreen()
                Role.ESTUDIANTE -> StudentHomeScreen(navController) // Los estudiantes ven un inicio separado similar al de profesores
                else -> HomeContent(ui = ui, onNavigate = { route -> navController.navigate(route) })
            }
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
    val isDemo = DemoData.isDemoUser()
    val unreadNotifications = if (isDemo) DemoData.unreadNotificationsCount() else ui.unreadNotifications
    val unreadMessages = if (isDemo) DemoData.unreadMessagesCount() else ui.unreadMessages
    val pagosDemo = if (isDemo) true else ui.pagosEnConsideracion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Saludo
        Text(
            text = if (!ui.name.isNullOrBlank()) stringResource(R.string.greeting, ui.name) else stringResource(R.string.home),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))

        // Badges rápidos
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(
                onClick = { onNavigate(AppRoutes.Notifications.route) },
                label = { Text(stringResource(R.string.unread_notifications, unreadNotifications)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
            AssistChip(
                onClick = { onNavigate(AppRoutes.Messages.route) },
                label = { Text(stringResource(R.string.unread_messages, unreadMessages)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Banner de pagos a considerar (solo estudiantes/padres)
        if (pagosDemo && ui.role != Role.DOCENTE && ui.role != Role.ADMIN) {
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
        when (ui.role) {
            Role.DOCENTE -> {
                SectionHeader(stringResource(R.string.my_academic_information))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Attendance.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.register_attendance))
                        }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Tasks.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.tasks))
                        }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Notes.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.notes))
                        }
                    }
                }
            }
            Role.PADRE -> {
                SectionHeader(stringResource(R.string.my_academic_information))
                // Enfocar accesos de tutoría
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Payments.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.payments)) }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Messages.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.messages)) }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Calendar.route) }) {
                        Box(Modifier.height(84.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.calendar)) }
                    }
                }
            }
            Role.ADMIN -> {
                SectionHeader("Administración")
                // Diseño más estético: 3 tarjetas con iconos y una sección específica para gestión de grupos
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.admin), style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Dashboard.route) }) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Profile.route) }) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Estado visible para toda la sección Admin: controlar diálogo de asignación
                var showAssignDialogHome by remember { mutableStateOf(false) }

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gestión de Grupos", style = MaterialTheme.typography.titleMedium)
                        Text("Asignar o quitar curso/grupo a docentes por email o UID. También puedes importar/exportar docentes en CSV.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { showAssignDialogHome = true }, modifier = Modifier.weight(1f)) {
                                Text("Asignar / Quitar grupo")
                            }
                            OutlinedButton(onClick = { onNavigate(AppRoutes.Admin.route) }, modifier = Modifier.weight(1f)) {
                                Text("Panel Admin")
                            }
                        }
                        if (showAssignDialogHome) {
                            AssignGroupDialog(onDismiss = { showAssignDialogHome = false })
                        }
                        //                        if (showAssignDialogHome) {
                        //                            AssignGroupDialog(onDismiss = { showAssignDialogHome = false })
                        //                        }
                        // Ahora la acción navegará a la pantalla dedicada de asignación
                     }
                 }
            }
            else -> {
                // ESTUDIANTE u otros
                SectionHeader(stringResource(R.string.my_academic_information))
                AcademicSection(onNavigate)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Accesos generales por rol
        val tiles: List<QuickTile> = when (ui.role) {
            Role.DOCENTE -> buildList {
                add(QuickTile(stringResource(R.string.calendar)) { onNavigate(AppRoutes.Calendar.route) })
                add(QuickTile(stringResource(R.string.messages)) { onNavigate(AppRoutes.Messages.route) })
                add(QuickTile(stringResource(R.string.profile)) { onNavigate(AppRoutes.Profile.route) })
            }
            Role.PADRE -> buildList {
                add(QuickTile(stringResource(R.string.transporte)) { onNavigate(AppRoutes.Transport.route) })
                add(QuickTile(stringResource(R.string.notifications)) { onNavigate(AppRoutes.Notifications.route) })
                add(QuickTile(stringResource(R.string.calendar)) { onNavigate(AppRoutes.Calendar.route) })
                add(QuickTile(stringResource(R.string.profile)) { onNavigate(AppRoutes.Profile.route) })
            }
            Role.ADMIN -> buildList {
                add(QuickTile(stringResource(R.string.notifications)) { onNavigate(AppRoutes.Notifications.route) })
                add(QuickTile(stringResource(R.string.messages)) { onNavigate(AppRoutes.Messages.route) })
                add(QuickTile("Padres") { onNavigate(AppRoutes.AdminParents.route) })
                add(QuickTile(stringResource(R.string.calendar)) { onNavigate(AppRoutes.Calendar.route) })
                // Se elimina el QuickTile de "Dashboard" aquí porque ya existe la tarjeta principal
                // en la sección de Administración (evita duplicado en el panel admin)
            }
            else -> buildList {
                add(QuickTile(stringResource(R.string.transporte)) { onNavigate(AppRoutes.Transport.route) })
                add(QuickTile(stringResource(R.string.calendar)) { onNavigate(AppRoutes.Calendar.route) })
                add(QuickTile(stringResource(R.string.payments)) { onNavigate(AppRoutes.Payments.route) })
                add(QuickTile(stringResource(R.string.profile)) { onNavigate(AppRoutes.Profile.route) })
            }
        }
        if (tiles.isNotEmpty()) QuickGrid(tiles)
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
                Box(Modifier
                    .height(96.dp)
                    .fillMaxWidth(), contentAlignment = Alignment.Center) {
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
            Box(Modifier
                .height(84.dp)
                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notes))
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Attendance.route) }) {
            Box(Modifier
                .height(84.dp)
                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.attendance))
            }
        }
        ElevatedCard(modifier = Modifier.weight(1f), onClick = { onNavigate(AppRoutes.Tasks.route) }) {
            Box(Modifier
                .height(84.dp)
                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tasks))
            }
        }
    }
}
