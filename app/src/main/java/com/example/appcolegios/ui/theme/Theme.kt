package com.example.appcolegios.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AppColegiosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    fontSizeEnum: Int = 1, // 0=Small,1=Normal,2=Large
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val light = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryDeep,
        onPrimaryContainer = OnPrimary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        tertiary = Tertiary,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = OutlineColor,
        onSurfaceVariant = OnSurfaceVariant,
        error = ErrorColor,
        outline = OutlineColor
    )
    val dark = darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryDeep,
        onPrimaryContainer = OnPrimary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        tertiary = Tertiary,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = OutlineColor,
        onSurfaceVariant = OnSurfaceVariant,
        error = ErrorColor,
        outline = OutlineColor
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> dark
        else -> light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            run { window.statusBarColor = PrimaryDeep.toArgb() }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Calcular factor de escala según enum
    val scale = when (fontSizeEnum) {
        0 -> 0.9f
        2 -> 1.15f
        else -> 1.0f
    }

    // Tipografía escalada (mantener mismas familias y pesos, solo ajustar tamaños)
    val scaledTypography = Typography(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22.sp.value * scale).sp,
            lineHeight = (28.sp.value * scale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (20.sp.value * scale).sp,
            lineHeight = (26.sp.value * scale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (16.sp.value * scale).sp,
            lineHeight = (24.sp.value * scale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (14.sp.value * scale).sp,
            lineHeight = (20.sp.value * scale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (12.sp.value * scale).sp,
            lineHeight = (16.sp.value * scale).sp
        )
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography,
        shapes = AppShapes,
        content = content
    )
}
