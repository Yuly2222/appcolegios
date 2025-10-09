package com.example.appcolegios.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentBlue,
    tertiary = BrightBlue,
    background = DarkBlue,
    surface = SurfaceDark,
    onPrimary = OnPrimaryDark,
    onSecondary = Color.White,
    onBackground = LightCyan,
    onSurface = LightCyan,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentBlue,
    tertiary = BrightBlue,
    background = LightCyan,
    surface = SurfaceLight,
    onPrimary = OnPrimaryLight,
    onSecondary = Color.White,
    onBackground = DarkBlue,
    onSurface = DarkBlue,
    error = ErrorRed
)

@Composable
fun AppColegiosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
