package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioSurfaceHighest = androidx.compose.ui.graphics.Color(0xFF28334A)

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = CoralCrimson,
    onSecondary = OnCoralCrimson,
    secondaryContainer = CoralCrimsonContainer,
    onSecondaryContainer = CoralCrimson,
    tertiary = SignalAmber,
    onTertiary = StudioVoid,
    tertiaryContainer = SignalAmberContainer,
    onTertiaryContainer = SignalAmber,
    background = StudioVoid,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioVoid,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHigh,
    surfaceContainerHighest = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = CoralCrimson,
    onError = OnCoralCrimson
)

@Composable
fun ReactStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        content = content
    )
}
