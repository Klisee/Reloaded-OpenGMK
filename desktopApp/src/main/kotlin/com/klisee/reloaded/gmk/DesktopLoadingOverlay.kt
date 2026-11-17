package com.klisee.reloaded.gmk

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La misma pantalla "Cargando..." que la app de Android: fondo oscuro con brillos, logo que
 * respira, texto y una barra que barre de lado a lado. Se dibuja sobre el launcher mientras el
 * motor arranca, así la ventana del juego aparece encima en vez de abrirse un rato después o
 * quedarse en negro. Consume los clics para que no se pueda pulsar lo que hay debajo.
 */
@Composable
fun DesktopLoadingOverlay(logo: Painter) {
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading-phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading-logo",
    )
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Se come los clics: si no, se podría pulsar "Jugar" a través del velo.
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        val glowCenter = Offset(maxWidth.value * 0.16f, maxHeight.value * 0.04f)
        val glowRadius = maxOf(maxWidth.value, maxHeight.value) * 0.78f
        val glow2Center = Offset(maxWidth.value * 0.98f, maxHeight.value * 0.22f)
        val glow2Radius = maxOf(maxWidth.value, maxHeight.value) * 0.52f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF050506),
                            Color(0xFF121315),
                            Color(0xFF1D1E21),
                            Color(0xFF09090A),
                        ),
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.055f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.03f), Color.Transparent),
                        center = glow2Center,
                        radius = glow2Radius,
                    ),
                ),
        )
        // Como en Android: el logo nunca pasa del 32% del lado corto, y la barra se queda corta
        // en pantallas pequeñas en vez de desbordar.
        val logoSize = minOf(160.dp, minOf(maxWidth, maxHeight) * 0.32f)
        val barWidth = minOf(184.dp, maxWidth * 0.30f)
        val scale = (minOf(maxWidth, maxHeight) / 640.dp).coerceIn(0.75f, 1.15f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(logoSize).alpha(0.95f * breathe),
            )
            Spacer(Modifier.height(18.dp * scale))
            Text(
                "Cargando...",
                color = Color(0xFFF7F8FA),
                fontSize = (20 * scale).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            )
            Spacer(Modifier.height(18.dp * scale))
            LoadingBar(phase, barWidth, 3.dp * scale)
        }
    }
}

@Composable
private fun LoadingBar(phase: Float, barWidth: Dp, barHeight: Dp) {
    val indicatorWidth = barWidth * 0.34f
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = (barWidth - indicatorWidth) * phase)
                .width(indicatorWidth)
                .height(barHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFEEF4FF).copy(alpha = 0.55f),
                            Color(0xFFEEF4FF).copy(alpha = 0.95f),
                            Color(0xFFEEF4FF).copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
fun DesktopOverlayRoot(visible: Boolean, logo: Painter, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (visible) {
            DesktopLoadingOverlay(logo)
        }
    }
}
