package com.klisee.reloaded.gmk

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val LocalLiquidGlass = staticCompositionLocalOf { false }

private val ReloadedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(44.dp),
)

@Composable
fun ReloadedTheme(
    theme: String = "material",
    fontFamily: FontFamily = FontFamily.SansSerif,
    colorSchemeOverride: ColorScheme? = null,
    shapesOverride: Shapes? = null,
    content: @Composable () -> Unit,
) {
    val base = Typography()
    val typography = remember(fontFamily) {
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
        )
    }
    val liquid = theme == "liquid"
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = colorSchemeOverride ?: if (liquid) liquidGlassScheme(dark) else platformColorScheme(dark)
    CompositionLocalProvider(LocalLiquidGlass provides liquid) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapesOverride ?: ReloadedShapes,
            typography = typography,
            content = content,
        )
    }
}

private fun liquidGlassScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color(0xFF0A84FF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0x330A84FF),
        onPrimaryContainer = Color(0xFFEAF4FF),
        secondary = Color(0xFFD6D9DE),
        onSecondary = Color(0xFF111214),
        secondaryContainer = Color(0x33FFFFFF),
        onSecondaryContainer = Color(0xFFF7F8FA),
        tertiary = Color(0xFFE8EAED),
        onTertiary = Color(0xFF111214),
        background = Color.Transparent,
        onBackground = Color(0xFFF7F8FA),
        surface = Color(0x66101012),
        onSurface = Color(0xFFF7F8FA),
        surfaceVariant = Color(0x33FFFFFF),
        onSurfaceVariant = Color(0xFFD6D9DE),
        outline = Color(0x3DFFFFFF),
        outlineVariant = Color(0x1FFFFFFF),
        inverseSurface = Color(0xFFF1F2F4),
        inverseOnSurface = Color(0xFF17181A),
        inversePrimary = Color(0xFF5E6065),
        scrim = Color.Black,
    ).copy(
        surfaceContainerLowest = Color(0xFF09090A),
        surfaceContainerLow = Color(0xFF101012),
        surfaceContainer = Color(0xFF151517),
        surfaceContainerHigh = Color(0xFF1A1B1E),
        surfaceContainerHighest = Color(0xFF202124),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5F0FF),
        onPrimaryContainer = Color(0xFF003A75),
        secondary = Color(0xFF4A4D53),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0x66FFFFFF),
        onSecondaryContainer = Color(0xFF18191C),
        tertiary = Color(0xFF3A3D43),
        onTertiary = Color(0xFFFFFFFF),
        background = Color.Transparent,
        onBackground = Color(0xFF151619),
        surface = Color(0x66FFFFFF),
        onSurface = Color(0xFF151619),
        surfaceVariant = Color(0x33FFFFFF),
        onSurfaceVariant = Color(0xFF4A4D53),
        outline = Color(0x2E000000),
        outlineVariant = Color(0x16000000),
        inverseSurface = Color(0xFF1B1C1F),
        inverseOnSurface = Color(0xFFF4F5F7),
        inversePrimary = Color(0xFFE1E3E7),
        scrim = Color.Black,
    ).copy(
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F8),
        surfaceContainer = Color(0xFFF0F1F3),
        surfaceContainerHigh = Color(0xFFE9EAED),
        surfaceContainerHighest = Color(0xFFE2E3E7),
    )
}

@Composable
fun LiquidGlassBackground() {
    val dark = isSystemInDarkTheme()
    val base = if (dark) {
        listOf(Color(0xFF050506), Color(0xFF121315), Color(0xFF1D1E21), Color(0xFF09090A))
    } else {
        listOf(Color(0xFFF8F8F9), Color(0xFFEAEBED), Color(0xFFDCDEE2), Color(0xFFFDFDFD))
    }
    val glow = if (dark) Color.White else Color.Black
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(base)),
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-140).dp, y = (-120).dp)
                .size(520.dp)
                .blur(64.dp)
                .background(
                    Brush.radialGradient(listOf(glow.copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .offset(x = 520.dp, y = 80.dp)
                .size(480.dp)
                .blur(64.dp)
                .background(
                    Brush.radialGradient(listOf(glow.copy(alpha = if (dark) 0.10f else 0.07f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .offset(x = 240.dp, y = 520.dp)
                .size(420.dp)
                .blur(56.dp)
                .background(
                    Brush.radialGradient(listOf(glow.copy(alpha = if (dark) 0.07f else 0.05f), Color.Transparent)),
                    CircleShape,
                ),
        )
    }
}
