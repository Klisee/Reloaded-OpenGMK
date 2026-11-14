package com.klisee.reloaded.gmk

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.highlight.Highlight

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberReloadedBackdrop(): LayerBackdrop = rememberLayerBackdrop()

@Composable
fun BackdropSource(backdrop: LayerBackdrop, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
}

@Composable
fun GlassPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    glassTint: Color = Color.White.copy(alpha = 0.055f),
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBackdrop.current
    if (backdrop == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fallbackColor,
            content = content,
        )
        return
    }
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(10.dp.toPx())
                lens(18.dp.toPx(), 32.dp.toPx())
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.16f)) },
            innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.18f) },
            onDrawSurface = {
                drawRect(glassTint)
            },
        ).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), shape),
    ) {
        content()
    }
}
