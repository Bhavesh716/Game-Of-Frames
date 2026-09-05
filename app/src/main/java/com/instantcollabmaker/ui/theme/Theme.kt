package com.instantcollabmaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FtColorScheme = darkColorScheme(
    primary = FtColor.Accent,
    onPrimary = FtColor.TextOnAccent,
    primaryContainer = FtColor.AccentSoft,
    onPrimaryContainer = FtColor.Accent,
    secondary = FtColor.TextSecondary,
    onSecondary = FtColor.Background,
    background = FtColor.Background,
    onBackground = FtColor.TextPrimary,
    surface = FtColor.Surface,
    onSurface = FtColor.TextPrimary,
    surfaceVariant = FtColor.SurfaceElevated,
    onSurfaceVariant = FtColor.TextSecondary,
    surfaceContainer = FtColor.SurfaceElevated,
    surfaceContainerHigh = FtColor.SurfaceHighest,
    outline = FtColor.Stroke,
    outlineVariant = FtColor.Divider,
    error = FtColor.Error,
    onError = FtColor.TextPrimary,
    errorContainer = FtColor.ErrorSoft,
    scrim = FtColor.Scrim,
)

/**
 * FrameTrace is deliberately a single-mode (dark, cinematic) product, so the system
 * dark-mode flag is accepted but does not switch palettes; a light equivalent would
 * undermine the design direction.
 */
@Composable
fun FrameTraceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FtColorScheme,
        typography = FtTypography,
        content = content,
    )
}
