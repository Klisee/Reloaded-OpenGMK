package com.klisee.reloaded.gmk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
actual fun TouchEditorScreen(settings: SettingsStore, onBack: () -> Unit) {
    TouchEditorOrientation()
    var values by remember { mutableStateOf(settings.touchLayouts) }
    var savedText by remember { mutableStateOf(TouchLayouts.encode(values)) }
    var profile by remember { mutableStateOf(TouchProfile.WORLD) }
    var selectedId by remember { mutableStateOf("joystick") }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val controls = remember(profile, values) { TouchLayouts.controlsFor(profile, values) }
    val selected = controls.firstOrNull { it.id == selectedId } ?: controls.firstOrNull()
    val dirty = TouchLayouts.encode(values) != savedText

    fun update(spec: TouchControlSpec, placement: TouchPlacement) {
        val key = TouchLayouts.key(profile, spec)
        values = if (placement == TouchLayouts.original(spec, profile) && !TouchLayouts.added(profile, spec)) {
            values - key
        } else {
            values + (key to placement)
        }
    }

    fun save() {
        runCatching { settings.touchLayouts = values }
            .onSuccess {
                savedText = TouchLayouts.encode(values)
                onBack()
            }
            .onFailure { error = it.message ?: "No se pudo guardar." }
    }

    fun exit() {
        if (dirty) confirmExit = true else onBack()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorPreviewPane(
                    profile = profile,
                    values = values,
                    selectedId = selected?.id,
                    keepAspect = settings.keepAspect,
                    opacity = settings.touchOpacity,
                    onBack = ::exit,
                    onProfile = {
                        profile = it
                        selectedId = TouchLayouts.controlsFor(it, values).firstOrNull()?.id.orEmpty()
                    },
                    onSelect = { selectedId = it },
                    onChange = ::update,
                    wide = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                EditorPanel(
                    values = values,
                    profile = profile,
                    controls = controls,
                    selected = selected,
                    dirty = dirty,
                    onSelect = { selectedId = it },
                    onChange = ::update,
                    onAdd = { spec ->
                        val placement = if (spec in controls) {
                            TouchLayouts.placement(values, profile, spec).copy(visible = true)
                        } else {
                            TouchPlacement(.5f, .5f)
                        }
                        values = values + (TouchLayouts.key(profile, spec) to placement)
                        selectedId = spec.id
                    },
                    onRemove = {
                        selected?.let { spec ->
                            values = values - TouchLayouts.key(profile, spec)
                            selectedId = TouchLayouts.controlsFor(profile, values).firstOrNull()?.id.orEmpty()
                        }
                    },
                    onReset = { confirmReset = true },
                    onSave = ::save,
                    modifier = Modifier.width(224.dp).fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorPreviewPane(
                    profile = profile,
                    values = values,
                    selectedId = selected?.id,
                    keepAspect = settings.keepAspect,
                    opacity = settings.touchOpacity,
                    onBack = ::exit,
                    onProfile = {
                        profile = it
                        selectedId = TouchLayouts.controlsFor(it, values).firstOrNull()?.id.orEmpty()
                    },
                    onSelect = { selectedId = it },
                    onChange = ::update,
                    wide = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                EditorPanel(
                    values = values,
                    profile = profile,
                    controls = controls,
                    selected = selected,
                    dirty = dirty,
                    onSelect = { selectedId = it },
                    onChange = ::update,
                    onAdd = { spec ->
                        val placement = if (spec in controls) {
                            TouchLayouts.placement(values, profile, spec).copy(visible = true)
                        } else {
                            TouchPlacement(.5f, .5f)
                        }
                        values = values + (TouchLayouts.key(profile, spec) to placement)
                        selectedId = spec.id
                    },
                    onRemove = {
                        selected?.let { spec ->
                            values = values - TouchLayouts.key(profile, spec)
                            selectedId = TouchLayouts.controlsFor(profile, values).firstOrNull()?.id.orEmpty()
                        }
                    },
                    onReset = { confirmReset = true },
                    onSave = ::save,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restablecer ${profile.title}") },
            text = { Text("Se recuperan posiciones, tamaños y visibilidad originales de este perfil. Los otros perfiles se conservan.") },
            confirmButton = {
                TextButton(onClick = {
                    values = TouchLayouts.resetProfile(values, profile)
                    selectedId = TouchLayouts.controlsFor(profile, values).firstOrNull()?.id.orEmpty()
                    confirmReset = false
                }) { Text("Restablecer") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Hay cambios en los controles que todavía no guardaste.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Descartar y salir") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir editando") } },
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("No se pudo guardar") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("Aceptar") } },
        )
    }
}

@Composable
private fun EditorHeader(
    profile: TouchProfile,
    onBack: () -> Unit,
    onProfile: (TouchProfile) -> Unit,
) {
    var profileMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("Volver") }
        Box {
            OutlinedButton(onClick = { profileMenu = true }) { Text("Perfil: ${profile.title}") }
            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                TouchProfile.entries.forEach { item ->
                    DropdownMenuItem(text = { Text(item.title) }, onClick = {
                        onProfile(item)
                        profileMenu = false
                    })
                }
            }
        }
    }
}

@Composable
private fun EditorPreviewPane(
    profile: TouchProfile,
    values: Map<String, TouchPlacement>,
    selectedId: String?,
    keepAspect: Boolean,
    opacity: Float,
    onBack: () -> Unit,
    onProfile: (TouchProfile) -> Unit,
    onSelect: (String) -> Unit,
    onChange: (TouchControlSpec, TouchPlacement) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorHeader(profile = profile, onBack = onBack, onProfile = onProfile)
        Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (wide) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                TouchPreview(
                    profile = profile,
                    values = values,
                    selectedId = selectedId,
                    keepAspect = keepAspect,
                    opacity = opacity,
                    onSelect = onSelect,
                    onChange = onChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            TouchPreview(
                profile = profile,
                values = values,
                selectedId = selectedId,
                keepAspect = keepAspect,
                opacity = opacity,
                onSelect = onSelect,
                onChange = onChange,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
        }
        Text(
            "Arrastrá un control para moverlo. También podés elegirlo en la lista.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "El juego cambia de perfil automáticamente.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EditorPanel(
    values: Map<String, TouchPlacement>,
    profile: TouchProfile,
    controls: List<TouchControlSpec>,
    selected: TouchControlSpec?,
    dirty: Boolean,
    onSelect: (String) -> Unit,
    onChange: (TouchControlSpec, TouchPlacement) -> Unit,
    onAdd: (TouchControlSpec) -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val candidates = TouchLayouts.availableFor(profile).filter {
        !it.joystick && (it !in controls || !TouchLayouts.placement(values, profile, it).visible)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Personalizar controles", style = MaterialTheme.typography.titleMedium)
                if (profile != TouchProfile.MENU) {
                    Box {
                        OutlinedButton(
                            onClick = { addMenu = true },
                            enabled = candidates.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Agregar botón") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                            candidates.forEach { spec ->
                                DropdownMenuItem(text = { Text(spec.title) }, onClick = {
                                    onAdd(spec)
                                    addMenu = false
                                })
                            }
                        }
                    }
                }
                if (selected == null) {
                    Text("Sin controles para editar en este perfil.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val placement = TouchLayouts.placement(values, profile, selected)
                    Box {
                        OutlinedButton(onClick = { controlMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected.title)
                        }
                        DropdownMenu(expanded = controlMenu, onDismissRequest = { controlMenu = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                            controls.forEach { spec ->
                                val shown = TouchLayouts.placement(values, profile, spec).visible
                                DropdownMenuItem(text = { Text(spec.title + if (shown) "" else " · oculto") }, onClick = {
                                    onSelect(spec.id)
                                    controlMenu = false
                                })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Visible", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = placement.visible, onCheckedChange = { onChange(selected, placement.copy(visible = it)) })
                    }
                    Text("Tamaño: ${(placement.scale * 100).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = placement.scale, onValueChange = { onChange(selected, placement.copy(scale = it)) }, valueRange = .5f..2f, steps = 29)
                    Text("Horizontal: ${(placement.x * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
                    Slider(value = placement.x, onValueChange = { onChange(selected, placement.copy(x = it)) }, valueRange = 0f..1f)
                    Text("Vertical: ${(placement.y * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
                    Slider(value = placement.y, onValueChange = { onChange(selected, placement.copy(y = it)) }, valueRange = 0f..1f)
                    TextButton(onClick = { onChange(selected, TouchLayouts.original(selected, profile)) }) {
                        androidx.compose.material3.Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Text("Restablecer este control")
                    }
                    if (TouchLayouts.added(profile, selected)) {
                        TextButton(onClick = onRemove) { Text("Quitar de este perfil") }
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Restablecer perfil") }
                }
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar y salir")
            }
        }
    }
}

@Composable
private fun TouchPreview(
    profile: TouchProfile,
    values: Map<String, TouchPlacement>,
    selectedId: String?,
    keepAspect: Boolean,
    opacity: Float,
    onSelect: (String) -> Unit,
    onChange: (TouchControlSpec, TouchPlacement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }
    val specs = remember(profile, values) { TouchLayouts.controlsFor(profile, values) }
    val currentValues by rememberUpdatedState(values)
    val select by rememberUpdatedState(onSelect)
    val update by rememberUpdatedState(onChange)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF131B25))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .onSizeChanged { size ->
                width = size.width
                height = size.height
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val viewport = TouchLayouts.viewport(size.width, size.height, keepAspect)
            if (keepAspect) {
                drawRect(Color(0xFF090C12))
                drawRect(
                    Color(0xFF131B25),
                    topLeft = Offset(viewport[0] * size.width, viewport[1] * size.height),
                    size = androidx.compose.ui.geometry.Size(viewport[2] * size.width, viewport[3] * size.height),
                )
            }
            for (i in 1..9) drawLine(Color.White.copy(alpha = .035f), Offset(size.width * i / 10f, 0f), Offset(size.width * i / 10f, size.height))
            for (i in 1..5) drawLine(Color.White.copy(alpha = .035f), Offset(0f, size.height * i / 6f), Offset(size.width, size.height * i / 6f))
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return@Box
        specs.forEach { spec ->
            val placement = TouchLayouts.placement(values, profile, spec)
            if (!placement.visible && spec.id != selectedId) return@forEach
            val geometry = TouchLayouts.geometry(spec, placement, w, h, keepAspect, TouchLayouts.added(profile, spec))
            if (geometry.radius <= 0f) return@forEach
            val isSelected = spec.id == selectedId
            val widthDp = with(density) { (geometry.halfWidth * 2f).toDp() }
            val heightDp = with(density) { (geometry.radius * 2f).toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((geometry.x - geometry.halfWidth).roundToInt(), (geometry.y - geometry.radius).roundToInt())
                    }
                    .size(widthDp, heightDp)
                    .alpha(if (placement.visible) 1f else .22f)
                    .clip(if (spec.asset != null) RoundedCornerShape(7.dp) else CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFF63E0CA) else Color.White.copy(alpha = 0.45f),
                        if (spec.asset != null) RoundedCornerShape(7.dp) else CircleShape,
                    )
                    .pointerInput(spec.id, profile, w, h, keepAspect) {
                        detectDragGestures(
                            onDragStart = { select(spec.id) },
                            onDrag = { change, drag ->
                                change.consume()
                                val current = TouchLayouts.placement(currentValues, profile, spec)
                                update(
                                    spec,
                                    TouchLayouts.moved(spec, current, drag.x, drag.y, w, h, keepAspect, TouchLayouts.added(profile, spec)),
                                )
                            },
                        )
                    }
                    .pointerInput(spec.id) { detectTapGestures { select(spec.id) } },
                contentAlignment = Alignment.Center,
            ) {
                val asset = if (spec.joystick) "joystick-base.png" else spec.asset
                val bitmap = asset?.let { touchAsset(it) }
                if (bitmap != null) {
                    Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (spec.joystick) {
                        val knob = touchAsset("joystick-knob.png")
                        if (knob != null) {
                            Image(knob, contentDescription = null, modifier = Modifier.fillMaxSize(.58f), contentScale = ContentScale.Fit)
                        }
                    }
                } else {
                    Text(
                        text = if (spec.joystick) "Joystick" else spec.label,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
