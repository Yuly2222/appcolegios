package com.example.appcolegios.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appcolegios.R
import java.util.Locale

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val state = vm.state.collectAsState().value

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        if (state.error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f))) {
                // state.error ya es no-null dentro de este bloque (comprobado arriba), así que no hace falta el elvis
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.refresh() }) { Text(stringResource(R.string.retry)) }
            return
        }

        // Gráfico de barras
        ChartSection(state)
        Spacer(Modifier.height(20.dp))

        // Pie chart adicional
        PieChartSection(state)

        Spacer(Modifier.height(20.dp))

        // Tarjetas de estadísticas
        StatsGrid(state)
    }
}

@Composable
private fun ChartSection(state: DashboardState) {
    Card(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.visual_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            val maxValue = maxOf(state.usersCount, state.studentsCount, state.teachersCount, state.groupsCount).toFloat()
            val chartData = listOf(
                BarData(stringResource(R.string.total_users), state.usersCount, MaterialTheme.colorScheme.primary),
                BarData(stringResource(R.string.students), state.studentsCount, MaterialTheme.colorScheme.tertiary),
                BarData(stringResource(R.string.teachers), state.teachersCount, MaterialTheme.colorScheme.secondary),
                BarData(stringResource(R.string.groups), state.groupsCount, Color(0xFF9C27B0))
            )

            SimpleBarChart(data = chartData, maxValue = if (maxValue > 0) maxValue else 1f)
        }
    }
}

@Composable
private fun SimpleBarChart(data: List<BarData>, maxValue: Float) {
    val barHeight = 40.dp
    val spacing = 12.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        data.forEach { barData ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = barData.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    val animatedProgress = animateFloatAsState(
                        targetValue = if (maxValue > 0) barData.value.toFloat() / maxValue else 0f,
                        label = "bar_${barData.label}"
                    ).value

                    Canvas(modifier = Modifier.fillMaxWidth().height(barHeight)) {
                        val barWidth = size.width * animatedProgress
                        drawRoundRect(
                            color = barData.color,
                            topLeft = Offset(0f, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                    Text(
                        text = barData.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private data class BarData(val label: String, val value: Int, val color: Color)

@Composable
private fun StatsGrid(state: DashboardState) {
    val cards = listOf(
        Triple(R.string.total_users, state.usersCount, MaterialTheme.colorScheme.primary),
        Triple(R.string.students, state.studentsCount, MaterialTheme.colorScheme.tertiary),
        Triple(R.string.teachers, state.teachersCount, MaterialTheme.colorScheme.secondary),
        Triple(R.string.groups, state.groupsCount, MaterialTheme.colorScheme.primaryContainer),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEach { (labelId, value, color) ->
            val scale = animateFloatAsState(targetValue = 1f, label = "stat_scale").value
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).graphicsLayer(scaleX = scale, scaleY = scale),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = stringResource(labelId), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = value.toString(), style = MaterialTheme.typography.displaySmall, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChartSection(state: DashboardState) {
    Card(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.distribution_users), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            val students = state.studentsCount.coerceAtLeast(0).toFloat()
            val teachers = state.teachersCount.coerceAtLeast(0).toFloat()
            val admins = (state.usersCount - state.studentsCount - state.teachersCount).coerceAtLeast(0).toFloat()
            val total = (students + teachers + admins).coerceAtLeast(1f)

            val sections = listOf(
                Pair(stringResource(R.string.students), students),
                Pair(stringResource(R.string.teachers), teachers),
                Pair(stringResource(R.string.others), admins)
            )

            SimplePieChart(values = sections.map { it.second }, colors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primaryContainer), labels = sections.map { it.first }, total = total)
        }
    }
}

@Composable
private fun SimplePieChart(values: List<Float>, colors: List<Color>, labels: List<String>, total: Float) {
    // Animar fracciones para que el gráfico tenga una transición suave
    val sweeps = values.map { (it / total) * 360f }
    val animatedSweeps = sweeps.map { animateFloatAsState(targetValue = it).value }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            val size = 160.dp
            Canvas(modifier = Modifier.size(size)) {
                var startAngle = -90f
                for (i in animatedSweeps.indices) {
                    val sweep = animatedSweeps[i]
                    drawArc(color = colors.getOrElse(i) { Color.Gray }, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.total_count, total.toInt()), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Leyenda con color y porcentaje
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val percentList = values.map { v -> if (total > 0f) (v / total * 100f) else 0f }
            for (i in labels.indices) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // color box
                    Box(modifier = Modifier.size(12.dp).background(colors.getOrElse(i) { Color.Gray }))
                    Spacer(Modifier.width(8.dp))
                    Text(text = labels.getOrElse(i) { "" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(text = "${String.format(Locale.US, "%.1f", percentList.getOrElse(i) { 0f })}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
