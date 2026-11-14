package com.klisee.reloaded.gmk
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect



import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Inicio", Icons.Outlined.Home, Icons.Filled.Home),
    Saves("Partidas", Icons.Outlined.Save, Icons.Filled.Save),
    Mods("Mods", Icons.Outlined.Build, Icons.Filled.Build),
    Logs("Logs", Icons.Outlined.Description, Icons.Filled.Description),
    Settings("Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
}

val MaterialTabs = listOf(AppTab.Home, AppTab.Logs, AppTab.Settings)

val BottomNavSpace = 104.dp

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "sin datos"
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}



@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val backdrop = LocalBackdrop.current
    val haptics = rememberInterfaceHaptics()
    val hapticClick = { haptics.confirm(); onClick() }
    if (LocalLiquidGlass.current && backdrop != null) {
        val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
        val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(12.dp.toPx(), 20.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(alpha = 0.8f) },
                    innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.25f) },
                    onDrawSurface = {
                        drawRect(glassTint)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.32f, size.height * 0.22f),
                                radius = size.minDimension * 0.85f,
                            ),
                        )
                    },
                )
                .border(1.dp, borderColor, CircleShape)
                .clickable(onClick = hapticClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            IconButton(onClick = hapticClick) { Icon(icon, contentDescription = label) }
        }
    }
}

@Composable
fun GroupListItem(
    headlineContent: @Composable () -> Unit,
    items: Int,
    index: Int,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val atEdge = items <= 1
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberInterfaceHaptics()
    val spec = tween<androidx.compose.ui.unit.Dp>(120)
    val top by animateDpAsState(
        targetValue = if (pressed) 40.dp else if (atEdge || index == 0) 20.dp else 4.dp,
        animationSpec = spec,
        label = "top",
    )
    val bottom by animateDpAsState(
        targetValue = if (pressed) 40.dp else if (atEdge || index == items - 1) 20.dp else 4.dp,
        animationSpec = spec,
        label = "bottom",
    )
    val shape = RoundedCornerShape(top, top, bottom, bottom)
    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                },
            ),
    )
    Spacer(Modifier.height(0.dp))
}

@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    items: Int,
    index: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GroupListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        items = items,
        index = index,
        modifier = modifier,
        onClick = if (enabled) onClick else null,
    )
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String, items: Int, index: Int) {
    GroupListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(icon, contentDescription = null) },
        items = items,
        index = index,
    )
}

@Composable
fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    items: Int,
    index: Int,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    GroupListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        items = items,
        index = index,
        onClick = if (enabled) onSelect else null,
    )
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    items: Int,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    GroupListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        items = items,
        index = index,
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
fun SliderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    items: Int,
    index: Int,
    onValueChange: (Float) -> Unit,
) {
    GroupListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                Slider(value = value, onValueChange = onValueChange, valueRange = 0.15f..1f)
            }
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        items = items,
        index = index,
    )
}

@Composable
fun ReloadedBackButton(onBack: () -> Unit) {
    Box(modifier = Modifier.padding(start = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun reloadedTopBarColors(): TopAppBarColors =
    if (LocalLiquidGlass.current) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

@Composable
fun DesktopSidebar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    version: String,
    modifier: Modifier = Modifier,
    tabs: List<AppTab> = AppTab.entries,
    logo: Painter? = null,
) {
    GlassPanel(
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = modifier.fillMaxHeight().width(248.dp),
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        glassTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (logo != null) {
                            Image(
                                painter = logo,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.CatchingPokemon,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Reloaded OpenGMK", style = MaterialTheme.typography.titleMedium)
                    Text("Launcher de Pokémon Reloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "NAVEGACIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(2.dp))

            tabs.forEach { tab ->
                val selected = current == tab
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!selected) onSelect(tab) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Reloaded OpenGMK", style = MaterialTheme.typography.labelLarge)
                    Text("v$version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Motor OpenGMK · ${platformLabel()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PcSidebar(
    current: AppTab,
    tabs: List<AppTab>,
    onSelect: (AppTab) -> Unit,
    onControls: () -> Unit,
    version: String,
    modifier: Modifier = Modifier,
    logo: Painter? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(236.dp).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (logo != null) {
                            Image(
                                painter = logo,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.CatchingPokemon,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Reloaded OpenGMK", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Launcher de Pokémon Reloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            tabs.forEach { tab ->
                val selected = current == tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .clickable { if (!selected) onSelect(tab) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                    )
                    Spacer(Modifier.width(9.dp))
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onControls)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Filled.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Mando",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "v$version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Text(
                "OpenGMK · ${platformLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun ReloadedBottomNav(current: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (LocalLiquidGlass.current) {
            val density = LocalDensity.current
            val haptics = LocalHapticFeedback.current
            val interfaceHaptics = rememberInterfaceHaptics()
            val backdrop = LocalBackdrop.current
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            var pressed by remember { mutableStateOf(false) }
            var panelOffset by remember { mutableStateOf(0f) }
            var touchPoint by remember { mutableStateOf(Offset.Zero) }
            val pressedTabs = remember { mutableStateMapOf<AppTab, Boolean>() }
            val tracker = remember { VelocityTracker() }
            val glow by animateFloatAsState(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                label = "bottom-nav-glow",
            )
            val target = dragFraction ?: current.ordinal.toFloat()
            val fraction by animateFloatAsState(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                label = "bottom-nav-pill",
            )
            val barScale by animateFloatAsState(
                targetValue = if (pressed) 1.043f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "bottom-nav-bar-scale",
            )
            val pillScale by animateFloatAsState(
                targetValue = if (pressed) 78f / 56f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
                label = "bottom-nav-pill-scale",
            )
            val tabScale by animateFloatAsState(
                targetValue = if (pressed) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "bottom-nav-tab-scale",
            )
            val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
            val navAccent = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)

            Box(
                modifier = Modifier
                    .widthIn(max = 368.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer {
                        translationX = panelOffset
                    },
            ) {
                GlassPanel(
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = barScale
                            scaleY = barScale
                        },
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                ) {}

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val innerPadding = 4.dp
                    val tabWidth = (maxWidth - innerPadding * 2) / AppTab.entries.size
                    val tabWidthPx = with(density) { tabWidth.toPx() }
                    val innerPaddingPx = with(density) { innerPadding.toPx() }
                    val pillShape = RoundedCornerShape(50)
                    val pillGlass = if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                if (glow < 0.999f) blur(8.dp.toPx() * (1f - glow))
                                lens(10.dp.toPx() * glow, 14.dp.toPx() * glow, chromaticAberration = true)
                            },
                            highlight = { Highlight.Default.copy(alpha = glow) },
                            shadow = { Shadow(alpha = glow) },
                            innerShadow = { InnerShadow(radius = 8.dp * glow, alpha = glow) },
                            onDrawSurface = {
                                val overlay = if (isLightTheme) Color.Black else Color.White
                                drawRect(overlay.copy(alpha = 0.12f * (1f - glow)))
                                drawRect(Color.Black.copy(alpha = 0.03f * glow))
                            },
                        )
                    } else {
                        Modifier.clip(pillShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = innerPadding + tabWidth * fraction)
                            .width(tabWidth)
                            .height(56.dp)
                            .graphicsLayer {
                                scaleX = pillScale
                                scaleY = pillScale
                            }
                            .then(pillGlass),
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithContent {
                                if (glow > 0f) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(Color.White.copy(alpha = 0.18f * glow), Color.Transparent),
                                            center = touchPoint,
                                            radius = size.minDimension * 1.5f,
                                        ),
                                        radius = size.minDimension * 1.5f,
                                        center = touchPoint,
                                    )
                                }
                                drawContent()
                            },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tabWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = { down ->
                                        pressed = true
                                        dragFraction = current.ordinal.toFloat()
                                        touchPoint = down
                                        panelOffset = 0f
                                        tracker.resetTracking()
                                        tracker.addPosition(System.nanoTime() / 1_000_000L, down)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    onDragCancel = {
                                        pressed = false
                                        dragFraction = null
                                        panelOffset = 0f
                                    },
                                    onDragEnd = {
                                        pressed = false
                                        val velocity = tracker.calculateVelocity().x
                                        var index = (dragFraction ?: current.ordinal.toFloat())
                                            .roundToInt()
                                            .coerceIn(0, AppTab.entries.lastIndex)
                                        if (abs(velocity) > 650f) {
                                            index = (index + if (velocity > 0f) 1 else -1)
                                                .coerceIn(0, AppTab.entries.lastIndex)
                                        }
                                        dragFraction = null
                                        panelOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (index != current.ordinal) onSelect(AppTab.entries[index])
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        val before = (dragFraction ?: current.ordinal.toFloat()).roundToInt()
                                        dragFraction = ((dragFraction ?: current.ordinal.toFloat()) + amount / tabWidthPx)
                                            .coerceIn(0f, AppTab.entries.lastIndex.toFloat())
                                        panelOffset = (panelOffset + amount)
                                            .coerceIn(-4.dp.toPx(), 4.dp.toPx())
                                        touchPoint = change.position
                                        tracker.addPosition(System.nanoTime() / 1_000_000L, change.position)
                                        if ((dragFraction ?: 0f).roundToInt() != before) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                )
                            }
                            .padding(horizontal = innerPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = current == tab
                            val interaction = remember { MutableInteractionSource() }
                            val tabPressed by interaction.collectIsPressedAsState()
                            LaunchedEffect(tabPressed) {
                                pressedTabs[tab] = tabPressed
                                if (tabPressed) {
                                    pressed = true
                                    touchPoint = Offset(
                                        innerPaddingPx + tabWidthPx * (tab.ordinal + 0.5f),
                                        with(density) { 32.dp.toPx() },
                                    )
                                } else if (pressedTabs.values.none { it }) {
                                    pressed = false
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null,
                                    ) {
                                        interfaceHaptics.tick()
                                        if (!selected) onSelect(tab)
                                    }
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                    },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (selected) navAccent else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (selected) navAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val interfaceHaptics = rememberInterfaceHaptics()
            GlassPanel(
                shape = RoundedCornerShape(32.dp),
                fallbackColor = MaterialTheme.colorScheme.primaryContainer,
                glassTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialTabs.forEach { tab ->
                        val selected = current == tab
                        Surface(
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        interfaceHaptics.confirm()
                                        if (!selected) onSelect(tab)
                                    }
                                    .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                AnimatedVisibility(visible = selected) {
                                    Row {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            tab.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
