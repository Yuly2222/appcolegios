package com.example.appcolegios.notificaciones

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Notification
import com.example.appcolegios.util.DateFormats
import kotlinx.coroutines.launch
import com.example.appcolegios.demo.DemoData

@Composable
fun NotificationsScreen(notificationsViewModel: NotificationsViewModel = viewModel()) {
    val uiState by notificationsViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var cutoffDays by remember { mutableStateOf<Int?>(7) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(cutoffDays) { notificationsViewModel.refresh(cutoffDays) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                Text("Mostrar últimos:")
                Spacer(Modifier.width(8.dp))
                Box {
                    Button(onClick = { expanded = true }) { Text(cutoffDays?.toString() ?: "Todos") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("7 días") }, onClick = { cutoffDays = 7; expanded = false })
                        DropdownMenuItem(text = { Text("30 días") }, onClick = { cutoffDays = 30; expanded = false })
                        DropdownMenuItem(text = { Text("90 días") }, onClick = { cutoffDays = 90; expanded = false })
                        DropdownMenuItem(text = { Text("Todos") }, onClick = { cutoffDays = null; expanded = false })
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                when {
                    uiState.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { Text(stringResource(R.string.error_label) + ": " + (uiState.error ?: ""), color = MaterialTheme.colorScheme.error) }
                    }
                    else -> {
                        val isDemo = DemoData.isDemoUser()
                        val dataToShow = if (uiState.notifications.isEmpty() && isDemo) DemoData.demoNotifications() else uiState.notifications
                        if (dataToShow.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { Text(stringResource(R.string.no_new_notifications), color = MaterialTheme.colorScheme.onBackground) }
                        } else {
                            NotificationList(
                                groupedNotifications = dataToShow,
                                onNotificationClick = { notification ->
                                    scope.launch { notificationsViewModel.markAsRead(notification.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationList(
    groupedNotifications: Map<String, List<Notification>>,
    onNotificationClick: (Notification) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groupedNotifications.forEach { (dateHeader, notifications) ->
            item(key = "header_$dateHeader") {
                val headerText = when (dateHeader) {
                    "Hoy" -> stringResource(R.string.today)
                    "Ayer" -> stringResource(R.string.yesterday)
                    else -> dateHeader
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(
                items = notifications,
                key = { it.id }
            ) { notification ->
                NotificationItem(notification = notification, onClick = { onNotificationClick(notification) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun NotificationItem(notification: Notification, onClick: () -> Unit) {
    val time = remember(notification.id, notification.fechaHora) { DateFormats.formatTime(notification.fechaHora) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = notification.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (notification.leida) FontWeight.Normal else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.cuerpo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val senderLabel = notification.senderName ?: notification.remitente
                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
