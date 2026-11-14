package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StormDarkColorScheme = darkColorScheme(
    primary = ChakraOrange,
    onPrimary = StormObsidian,
    primaryContainer = ChakraOrangeDim,
    onPrimaryContainer = StormTextPrimary,
    secondary = RasenganCyan,
    onSecondary = StormObsidian,
    secondaryContainer = StormSurfaceBright,
    onSecondaryContainer = StormTextPrimary,
    tertiary = ChakraAmber,
    onTertiary = StormObsidian,
    background = StormObsidian,
    onBackground = StormTextPrimary,
    surface = StormVoid,
    onSurface = StormTextPrimary,
    surfaceVariant = StormSurface,
    onSurfaceVariant = StormTextSecondary,
    surfaceContainer = StormSurface,
    surfaceContainerHigh = StormSurfaceElevated,
    surfaceContainerHighest = StormSurfaceBright,
    outline = StormOutline,
    outlineVariant = StormOutlineSubtle,
    error = StatusError,
    onError = StormObsidian
)

private val StormShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun StormLauncherTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StormDarkColorScheme,
        typography = StormTypography,
        shapes = StormShapes,
        content = content
    )
}
