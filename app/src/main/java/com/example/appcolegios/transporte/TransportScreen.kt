package com.example.appcolegios.transporte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp // import explícito para dp
import com.example.appcolegios.R
import com.example.appcolegios.data.model.TransportMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportScreen() {
    // Suponiendo que este flag vendría de Firebase o configuración local
    val esColegioPublico = true

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.transporte)) })
        }
    ) { paddingValues ->
        if (esColegioPublico) {
            TransportAssistanceContent(modifier = Modifier.padding(paddingValues))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.rutas_no_aplican))
            }
        }
    }
}

@Composable
fun TransportAssistanceContent(modifier: Modifier = Modifier) {
    val transportOptions = TransportMode.entries.map { mode ->
        mode.name.replace('_', ' ').replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
        }
    }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.how_did_you_get_here_today),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))
        transportOptions.forEach { text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = (text == selectedOption),
                        onClick = { selectedOption = text }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { selectedOption = text }
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { /* TODO registrar asistencia */ },
            enabled = selectedOption != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text(stringResource(R.string.register_attendance)) }
    }
}
