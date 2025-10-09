package com.example.appcolegios.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.appcolegios.R
import com.example.appcolegios.auth.AuthViewModel
import com.example.appcolegios.auth.AuthViewModelFactory
import com.example.appcolegios.data.UserPreferencesRepository
import com.example.appcolegios.data.model.Role
import com.example.appcolegios.ui.theme.BrightBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferencesRepository(context) }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(userPrefs))
    val homeViewModel: HomeViewModel = viewModel(factory = object: androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(userPrefs) as T
        }
    })
    val ui by homeViewModel.ui.collectAsState()

    // Construir acciones rápidas en línea, usando stringResource dentro de remember
    val quickActions = remember(ui.role, ui.pagosEnConsideracion) {
        val base = listOf(
            QuickAction(stringResource(R.string.notes), Icons.AutoMirrored.Filled.Assignment, "results"),
            QuickAction(stringResource(R.string.attendance), Icons.Filled.CheckCircle, "attendance"),
            QuickAction(stringResource(R.string.tasks), Icons.AutoMirrored.Filled.ListAlt, "tasks"),
            QuickAction(stringResource(R.string.calendar), Icons.Filled.CalendarToday, "calendar"),
            QuickAction(stringResource(R.string.notifications), Icons.Filled.Notifications, "notifications"),
            QuickAction(stringResource(R.string.messages), Icons.Filled.MailOutline, "inbox"),
            QuickAction(stringResource(R.string.payments), Icons.Filled.Payment, "payments"),
            QuickAction(stringResource(R.string.profile), Icons.Filled.Person, "profile"),
            QuickAction(stringResource(R.string.my_academic_information), Icons.Filled.Info, "academics"),
            QuickAction(stringResource(R.string.transporte), Icons.Filled.DirectionsBus, "transport")
        )
        when (ui.role) {
            Role.ESTUDIANTE -> base
            Role.PADRE -> base.filterNot { it.route == "tasks" }
            Role.DOCENTE -> base.filterNot { it.route == "payments" }
            null -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home))
                    }
                },
                actions = {
                    BadgedIconButton(count = ui.unreadNotifications, onClick = { navController.navigate("notifications") }) {
                        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications))
                    }
                    BadgedIconButton(count = ui.unreadMessages, onClick = { navController.navigate("messages") }) {
                        Icon(Icons.Filled.MailOutline, contentDescription = stringResource(R.string.messages))
                    }
                    IconButton(onClick = {
                        authViewModel.signOut()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cerrar_sesion))
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ui.error != null -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error ?: stringResource(R.string.error_generic))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { /* retry placeholder */ }) { Text(stringResource(R.string.retry)) }
                }
            }
            quickActions.isEmpty() -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_home))
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(12.dp)
                ) {
                    GreetingCard(name = ui.name ?: "", role = ui.role, selectorVisible = ui.role == Role.PADRE)
                    Spacer(Modifier.height(16.dp))
                    if (ui.pagosEnConsideracion) PaymentsBanner()
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(quickActions) { item ->
                            HomeCard(item) { navController.navigate(item.route) }
                        }
                    }
                }
            }
        }
    }
}

private data class QuickAction(val title: String, val icon: ImageVector, val route: String)

@Composable
private fun GreetingCard(name: String, role: Role?, selectorVisible: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BrightBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.greeting, name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    role?.let { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
                if (selectorVisible) {
                    OutlinedButton(onClick = { /* seleccionar estudiante */ }) { Text(stringResource(R.string.select_student)) }
                }
            }
        }
    }
}

@Composable
private fun PaymentsBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.payments_to_consider), color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BadgedIconButton(count: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    BadgedBox(badge = {
        if (count > 0) Badge { Text(count.toString()) }
    }) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun HomeCard(item: QuickAction, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
