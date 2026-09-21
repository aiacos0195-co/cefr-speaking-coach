package com.cefrspeakingcoach.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors: ColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE7FF),
    onPrimaryContainer = Color(0xFF1B1452),

    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8FF),
    onSecondaryContainer = Color(0xFF0D2552),

    tertiary = BrandTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8FFF4),
    onTertiaryContainer = Color(0xFF003B2F),

    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF121418),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121418),

    surfaceVariant = Color(0xFFF0F1F6),
    onSurfaceVariant = Color(0xFF444B57),

    outline = Color(0xFFB8BECC),
    outlineVariant = Color(0xFFD6DAE5),

    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBDB6FF),
    onPrimary = Color(0xFF1A1452),
    primaryContainer = Color(0xFF3B2FD6),
    onPrimaryContainer = Color(0xFFEAE7FF),

    secondary = Color(0xFFB3CCFF),
    onSecondary = Color(0xFF0D2552),
    secondaryContainer = Color(0xFF2D6CDF),
    onSecondaryContainer = Color(0xFFDDE8FF),

    tertiary = Color(0xFF7DEBD0),
    onTertiary = Color(0xFF003B2F),
    tertiaryContainer = Color(0xFF16A085),
    onTertiaryContainer = Color(0xFFD8FFF4),

    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE7E9EF),

    surface = Color(0xFF121418),
    onSurface = Color(0xFFE7E9EF),

    surfaceVariant = Color(0xFF1B1F27),
    onSurfaceVariant = Color(0xFFBFC6D6),

    outline = Color(0xFF6E7687),
    outlineVariant = Color(0xFF2A2F3A),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val AppTypography = Typography()

@Composable
fun CEFRSpeakingCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
