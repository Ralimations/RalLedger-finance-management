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
        primary = Color(0xFF0066CC), darkPrimary = Color(0xFF9CCBFF), onDarkPrimary = Color(0xFF003260),
        container = Color(0xFFD7EAFE), onContainer = Color(0xFF092745), darkContainer = Color(0xFF064B8D), onDarkContainer = Color(0xFFD7EAFE),
        secondary = Color(0xFF087E9B), tertiary = Color(0xFF6250A4)
    )
    ThemePalette.INDIGO -> PaletteColors(
        primary = Color(0xFF4F46B5), darkPrimary = Color(0xFFC2C1FF), onDarkPrimary = Color(0xFF292866),
        container = Color(0xFFE2DFFF), onContainer = Color(0xFF17164A), darkContainer = Color(0xFF4544AB), onDarkContainer = Color(0xFFE2DFFF),
        secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260)
    )
    ThemePalette.SAGE -> PaletteColors(
        primary = Color(0xFF486235), darkPrimary = Color(0xFFB8D49E), onDarkPrimary = Color(0xFF243816),
        container = Color(0xFFD8EAC8), onContainer = Color(0xFF111F08), darkContainer = Color(0xFF3A5129), onDarkContainer = Color(0xFFD8EAC8),
        secondary = Color(0xFF5A6350), tertiary = Color(0xFF3E6664)
    )
    ThemePalette.CORAL -> PaletteColors(
        primary = Color(0xFFBC344A), darkPrimary = Color(0xFFFFB3C0), onDarkPrimary = Color(0xFF5D0015),
        container = Color(0xFFFFE0E5), onContainer = Color(0xFF39000A), darkContainer = Color(0xFF8E1D32), onDarkContainer = Color(0xFFFFE0E5),
        secondary = Color(0xFF81565E), tertiary = Color(0xFF6B5B8E)
    )
    ThemePalette.AMBER -> PaletteColors(
        primary = Color(0xFF925400), darkPrimary = Color(0xFFFFB957), onDarkPrimary = Color(0xFF3A1C00),
        container = Color(0xFFFFE2B8), onContainer = Color(0xFF321B00), darkContainer = Color(0xFF704000), onDarkContainer = Color(0xFFFFE2B8),
        secondary = Color(0xFF745B3B), tertiary = Color(0xFF55633B)
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
