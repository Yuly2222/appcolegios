package com.example.appcolegios.mensajes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.appcolegios.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onUserSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: NewMessageViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(uiState.users, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) uiState.users else uiState.users.filter { it.name.lowercase().contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = stringResource(id = R.string.search_by_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(uiState.error ?: "") }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUserSelected(user.id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = user.avatarUrl,
                                        contentDescription = user.name,
                                        modifier = Modifier.size(48.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                        fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = user.name, style = MaterialTheme.typography.titleMedium)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
