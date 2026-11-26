package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = GoogleBlueContainer,
    onPrimaryContainer = GoogleBlueOnContainer,
    secondary = GoogleBlue,
    onSecondary = Color.White,
    secondaryContainer = GoogleBlueContainer,
    onSecondaryContainer = GoogleBlueOnContainer,
    tertiary = GoogleBlue,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = StatusRedText,
    onError = Color.White,
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRedText
)

private val DarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = GoogleBlueDarkContainer,
    onPrimaryContainer = GoogleBlueDarkOnContainer,
    secondary = GoogleBlueDark,
    onSecondary = Color(0xFF062E6F),
    secondaryContainer = GoogleBlueDarkContainer,
    onSecondaryContainer = GoogleBlueDarkOnContainer,
    tertiary = GoogleBlueDark,
    onTertiary = Color(0xFF062E6F),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = StatusRedDarkText,
    onError = Color(0xFF601410),
    errorContainer = StatusRedDarkBg,
    onErrorContainer = StatusRedDarkText
)

val AcademyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AcademyManagerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AcademyShapes,
        content = content
    )
}
