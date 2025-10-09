package com.example.appcolegios.util

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appcolegios.R

@Composable
fun <T> UiStateContainer(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    success: @Composable (T) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is UiState.Error -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.message ?: stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
                if (onRetry != null) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            UiState.Empty -> emptyContent?.invoke() ?: Text(
                text = stringResource(R.string.empty_home),
                modifier = Modifier.align(Alignment.Center)
            )
            is UiState.Success -> success(state.data)
        }
    }
}

