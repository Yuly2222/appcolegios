package com.example.appcolegios.notificaciones

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import com.example.appcolegios.data.model.Notification
import com.example.appcolegios.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(notificationsViewModel: NotificationsViewModel = viewModel()) {
    val uiState by notificationsViewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.notifications)) }) }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.error_label) + ": " + (uiState.error ?: "")) }
            }
            uiState.notifications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.no_new_notifications)) }
            }
            else -> {
                NotificationList(
                    groupedNotifications = uiState.notifications,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun NotificationList(
    groupedNotifications: Map<String, List<Notification>>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groupedNotifications.forEach { (dateHeader, notifications) ->
            item(key = "header_$dateHeader") {
                Text(
                    text = dateHeader,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(
                items = notifications,
                key = { it.id }
            ) { notification ->
                NotificationItem(notification = notification, onClick = { /* marcar leída */ })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun NotificationItem(notification: Notification, onClick: () -> Unit) {
    val time = remember { DateFormats.formatTime(notification.fechaHora) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = notification.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (notification.leida) FontWeight.Normal else FontWeight.Bold
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
                Text(
                    text = notification.remitente,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
