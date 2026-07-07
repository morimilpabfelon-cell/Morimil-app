package com.morimil.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val MorimilLightColors = lightColorScheme(
    primary = Color(0xFF245C37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EACF),
    onPrimaryContainer = Color(0xFF06210F),
    secondary = Color(0xFF6B5B1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2A6),
    onSecondaryContainer = Color(0xFF211A02),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAA),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF4F2EA),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE9EEDF),
    onSurfaceVariant = Color(0xFF44483F),
    error = Color(0xFFBA1A1A)
)

private val MorimilDarkColors = darkColorScheme(
    primary = Color(0xFF9DD7A6),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF0D4A25),
    onPrimaryContainer = Color(0xFFC8EACF),
    secondary = Color(0xFFD8C77C),
    onSecondary = Color(0xFF393000),
    secondaryContainer = Color(0xFF514700),
    onSecondaryContainer = Color(0xFFF5E6A4),
    tertiary = Color(0xFFFFC36F),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE3E3D8),
    surface = Color(0xFF171A14),
    onSurface = Color(0xFFE3E3D8),
    surfaceVariant = Color(0xFF3F453C),
    onSurfaceVariant = Color(0xFFC5C9BD),
    error = Color(0xFFFFB4AB)
)

val MorimilPillShape = RoundedCornerShape(24.dp)
val MorimilBubbleUserShape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
val MorimilBubbleAiShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
val MorimilAccentWarm = Color(0xFFBA7517)

@Composable
fun morimilBackgroundBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF11140F), Color(0xFF141A12)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF4F2EA), Color(0xFFE9EEDF)))
    }
}

@Composable
fun MorimilTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MorimilDarkColors
        else -> MorimilLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
