package com.ralledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private data class PaletteColors(
    val primary: Color,
    val darkPrimary: Color,
    val onDarkPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
    val secondary: Color,
    val tertiary: Color
)

private fun ThemePalette.colors() = when (this) {
    ThemePalette.OCEAN -> PaletteColors(
        primary = Color(0xFF006B63), darkPrimary = Color(0xFF80D5C8), onDarkPrimary = Color(0xFF003731),
        container = Color(0xFFBCECE4), onContainer = Color(0xFF00201D), darkContainer = Color(0xFF005048), onDarkContainer = Color(0xFFBCECE4),
        secondary = Color(0xFF486560), tertiary = Color(0xFF456179)
    )
    ThemePalette.INDIGO -> PaletteColors(
        primary = Color(0xFF5B5BD6), darkPrimary = Color(0xFFC2C1FF), onDarkPrimary = Color(0xFF292866),
        container = Color(0xFFE2DFFF), onContainer = Color(0xFF17164A), darkContainer = Color(0xFF4544AB), onDarkContainer = Color(0xFFE2DFFF),
        secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260)
    )
    ThemePalette.SAGE -> PaletteColors(
        primary = Color(0xFF526B3F), darkPrimary = Color(0xFFB8D49E), onDarkPrimary = Color(0xFF243816),
        container = Color(0xFFD8EAC8), onContainer = Color(0xFF111F08), darkContainer = Color(0xFF3A5129), onDarkContainer = Color(0xFFD8EAC8),
        secondary = Color(0xFF5A6350), tertiary = Color(0xFF3E6664)
    )
}

@Composable
fun RalLedgerTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = settings.palette.colors()
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.darkPrimary,
            onPrimary = colors.onDarkPrimary,
            primaryContainer = colors.darkContainer,
            onPrimaryContainer = colors.onDarkContainer,
            secondary = colors.secondary,
            tertiary = colors.tertiary,
            background = BackgroundDark,
            surface = SurfaceDark,
            onBackground = TextPrimaryDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = Color(0xFF343538),
            onSurfaceVariant = TextSecondaryDark
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            secondary = colors.secondary,
            tertiary = colors.tertiary,
            background = BackgroundLight,
            surface = SurfaceLight,
            onBackground = TextPrimaryLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = Color(0xFFE9E7EB),
            onSurfaceVariant = TextSecondaryLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
