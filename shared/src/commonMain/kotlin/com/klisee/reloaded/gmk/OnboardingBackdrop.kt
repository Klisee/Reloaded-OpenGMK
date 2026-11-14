package com.klisee.reloaded.gmk

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Fondo del onboarding: cintas suaves dibujadas con degradados radiales.
 *
 * Cada cinta es una cadena de óvalos difuminados a lo largo de una diagonal; al
 * no tener bordes rectos nunca aparecen cortes ni "roturas". Las tres cintas
 * comparten reloj y van separadas un tercio del recorrido, así el bucle es
 * continuo y jamás se solapan.
 */
@Composable
fun OnboardingBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "onboarding-backdrop")

    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 46_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "float",
    )

    val primary = scheme.primary
    val tertiary = scheme.tertiary
    val secondary = scheme.secondary
    val onSurface = scheme.onSurface

    val particles = remember {
        List(24) { index ->
            Triple(
                (index * 0.6180339887f) % 1f,
                (index * 0.4142135623f) % 1f,
                0.5f + (index % 5) * 0.25f,
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.13f + 0.05f * shimmer),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.26f),
                radius = size.width * (0.95f + 0.07f * shimmer),
            ),
        )

        drawSoftRibbon(
            wave = wave,
            phase = 0f,
            color = primary.copy(alpha = 0.20f),
            heightFactor = 0.20f,
            slope = -0.55f,
        )
        drawSoftRibbon(
            wave = wave,
            phase = 1f / 3f,
            color = tertiary.copy(alpha = 0.14f),
            heightFactor = 0.15f,
            slope = -0.42f,
        )
        drawSoftRibbon(
            wave = wave,
            phase = 2f / 3f,
            color = secondary.copy(alpha = 0.11f),
            heightFactor = 0.12f,
            slope = -0.66f,
        )

        drawMotes(particles, float, onSurface)
        drawDotGrid(onSurface)
    }
}

/** Arranca por debajo de la pantalla y sale por arriba: el reinicio no se ve. */
private fun bandY(height: Float, travel: Float): Float = height * (1.45f - 2.10f * travel)

private fun DrawScope.drawSoftRibbon(
    wave: Float,
    phase: Float,
    color: Color,
    heightFactor: Float,
    slope: Float,
) {
    val w = size.width
    val h = size.height
    val travel = (wave + phase) % 1f
    val centerY = bandY(h, travel)

    val segments = 9
    for (index in 0 until segments) {
        val t = index / (segments - 1f)
        val cx = -w * 0.18f + t * w * 1.36f
        val cy = centerY + (t - 0.5f) * h * slope
        val radiusX = w * (0.26f + 0.04f * sin((t * PI).toFloat()))
        val radiusY = h * heightFactor * (0.7f + 0.3f * sin((t * PI).toFloat()))
        val radius = max(radiusX, radiusY)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.35f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius * 1.05f,
            ),
            topLeft = Offset(cx - radiusX, cy - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
        )
    }
}

private fun DrawScope.drawMotes(
    particles: List<Triple<Float, Float, Float>>,
    float: Float,
    onSurface: Color,
) {
    val w = size.width
    val h = size.height
    particles.forEach { (baseX, baseY, scale) ->
        val angle = (float + baseX) * 2f * PI.toFloat()
        val x = baseX * w + sin(angle) * 7f
        val y = baseY * h + sin((float + baseY) * 2f * PI.toFloat()) * (h * 0.012f)
        val pulse = (sin(angle * 1.7f) + 1f) / 2f
        drawCircle(
            color = onSurface.copy(alpha = 0.05f + 0.09f * pulse),
            radius = 1.4f * scale + 0.5f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawDotGrid(onSurface: Color) {
    val step = 38f
    val color = onSurface.copy(alpha = 0.03f)
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color = color, radius = 0.9f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
