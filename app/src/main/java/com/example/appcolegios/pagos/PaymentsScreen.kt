package com.example.appcolegios.pagos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.appcolegios.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.payments)) }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Capa gradiente
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
            // Imagen difuminada de fondo
            Image(
                painter = painterResource(R.drawable.sticker_payments),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(40.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .alpha(0.35f)
            )
            // Contenido principal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.sticker_payments),
                            contentDescription = stringResource(R.string.payments_to_consider),
                            contentScale = ContentScale.Cover,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.payments_to_consider),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.home)) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
    }
}
