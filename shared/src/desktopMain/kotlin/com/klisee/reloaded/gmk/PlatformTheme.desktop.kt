package com.klisee.reloaded.gmk

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Paleta de escritorio al estilo Windows 11 (Fluent / Mica).
 *
 * Antes esto devolvía `darkColorScheme()` / `lightColorScheme()` de Material 3 sin tocar, que son
 * morados y no se parecen a Windows. Aquí se replica la paleta neutra azulada de Windows 11:
 * superficies gris-azuladas, acento azul del sistema y contornos translúcidos.
 *
 * Se usa el mismo juego de colores en Windows y Linux: en Linux las superficies sólidas también
 * se ven bien y evita dos estéticas distintas para la misma app de escritorio.
 */
private object Win11 {
    // Acento: el azul de Windows 11 (accent base #0078D4, claro #4CC2FF en oscuro)
    val AccentLight = Color(0xFF0067C0)
    val AccentDark = Color(0xFF4CC2FF)
    val OnAccentLight = Color(0xFFFFFFFF)
    val OnAccentDark = Color(0xFF00243D)

    // Superficies oscuras: gris muy azulado, como la Mica oscura
    val BgDark = Color(0xFF1C1C1C)
    val SurfaceDark = Color(0xFF202020)
    val SurfaceLowDark = Color(0xFF262626)
    val SurfaceHighDark = Color(0xFF2D2D2D)
    val SurfaceHighestDark = Color(0xFF333333)
    val TextDark = Color(0xFFF5F5F5)
    val TextMutedDark = Color(0xFFB0B0B0)
    val OutlineDark = Color(0xFF3D3D3D)

    // Superficies claras: blanco roto con tinte azul, como la Mica clara
    val BgLight = Color(0xFFF3F3F3)
    val SurfaceLight = Color(0xFFFAFAFA)
    val SurfaceLowLight = Color(0xFFF6F6F6)
    val SurfaceHighLight = Color(0xFFEFEFEF)
    val SurfaceHighestLight = Color(0xFFE8E8E8)
    val TextLight = Color(0xFF1A1A1A)
    val TextMutedLight = Color(0xFF5C5C5C)
    val OutlineLight = Color(0xFFD6D6D6)
}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme =
    if (dark) windows11DarkScheme() else windows11LightScheme()

private fun windows11DarkScheme(): ColorScheme = darkColorScheme(
    primary = Win11.AccentDark,
    onPrimary = Win11.OnAccentDark,
    primaryContainer = Color(0xFF1B3A4B),
    onPrimaryContainer = Color(0xFFC9E7FF),
    secondary = Color(0xFF9BCBEA),
    onSecondary = Color(0xFF00344E),
    secondaryContainer = Color(0xFF2A3F4D),
    onSecondaryContainer = Color(0xFFD6ECFF),
    tertiary = Color(0xFFB8C8D9),
    onTertiary = Color(0xFF1F2A33),
    tertiaryContainer = Color(0xFF35424D),
    onTertiaryContainer = Color(0xFFE3EEF9),
    background = Win11.BgDark,
    onBackground = Win11.TextDark,
    surface = Win11.SurfaceDark,
    onSurface = Win11.TextDark,
    surfaceVariant = Win11.SurfaceHighDark,
    onSurfaceVariant = Win11.TextMutedDark,
    outline = Win11.OutlineDark,
    outlineVariant = Color(0xFF2E2E2E),
    inverseSurface = Win11.TextDark,
    inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Win11.AccentLight,
    scrim = Color.Black,
    surfaceTint = Win11.AccentDark,
    error = Color(0xFFFF99A4),
    onError = Color(0xFF68000B),
    errorContainer = Color(0xFF8C1D2A),
    onErrorContainer = Color(0xFFFFDAD9),
).copy(
    surfaceBright = Color(0xFF3A3A3A),
    surfaceDim = Color(0xFF161616),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Win11.SurfaceLowDark,
    surfaceContainer = Win11.SurfaceDark,
    surfaceContainerHigh = Win11.SurfaceHighDark,
    surfaceContainerHighest = Win11.SurfaceHighestDark,
)

private fun windows11LightScheme(): ColorScheme = lightColorScheme(
    primary = Win11.AccentLight,
    onPrimary = Win11.OnAccentLight,
    primaryContainer = Color(0xFFD3E8FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF2C5F7C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E9F8),
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = Color(0xFF44546A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E3F0),
    onTertiaryContainer = Color(0xFF0D1C2B),
    background = Win11.BgLight,
    onBackground = Win11.TextLight,
    surface = Win11.SurfaceLight,
    onSurface = Win11.TextLight,
    surfaceVariant = Win11.SurfaceHighLight,
    onSurfaceVariant = Win11.TextMutedLight,
    outline = Win11.OutlineLight,
    outlineVariant = Color(0xFFE4E4E4),
    inverseSurface = Color(0xFF2E2E2E),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Win11.AccentDark,
    scrim = Color.Black,
    surfaceTint = Win11.AccentLight,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
).copy(
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Win11.SurfaceLowLight,
    surfaceContainer = Win11.SurfaceLight,
    surfaceContainerHigh = Win11.SurfaceHighLight,
    surfaceContainerHighest = Win11.SurfaceHighestLight,
)
