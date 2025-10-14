package com.example.appcolegios.mensajes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.appcolegios.R
import com.example.appcolegios.navigation.AppRoutes
import com.example.appcolegios.util.DateFormats

@Composable
fun ConversationsScreen(navController: NavController, conversationsViewModel: ConversationsViewModel = viewModel()) {
    val uiState by conversationsViewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.error_label) + ": " + (uiState.error ?: "")) }
            }
            else -> {
                ConversationList(
                    conversations = uiState.conversations,
                    onConversationClick = { otherUserId -> navController.navigate("chat/$otherUserId") },
                    modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)
                )
            }
        }
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "fab_scale")
        FloatingActionButton(
            onClick = { navController.navigate(AppRoutes.NewMessage.route) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).graphicsLayer(scaleX = scale, scaleY = scale),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            interactionSource = interaction
        ) { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = stringResource(R.string.messages)) }
    }
}

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(conversations) { conversation ->
            ConversationItem(conversation = conversation, onClick = { onConversationClick(conversation.id) })
            HorizontalDivider()
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    val time = DateFormats.formatTime(conversation.timestamp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = conversation.otherUserAvatarUrl,
            contentDescription = stringResource(R.string.avatar_de, conversation.otherUserName),
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            fallback = painterResource(id = R.drawable.ic_launcher_foreground)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.otherUserName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
