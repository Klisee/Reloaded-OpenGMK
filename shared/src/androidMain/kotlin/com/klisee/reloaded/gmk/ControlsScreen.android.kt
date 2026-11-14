package com.klisee.reloaded.gmk

import android.content.Context
import android.hardware.input.InputManager
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun actionFor(bindings: Map<ControlAction, Int>, code: Int): ControlAction? =
    ControlAction.entries.firstOrNull { (bindings[it] ?: it.defaultCode) == code }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ControlsScreen(settings: SettingsStore, pads: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var bindings by remember { mutableStateOf(settings.controlBindings) }
    var stick by remember { mutableStateOf(settings.stickEnabled) }
    var capturing by remember { mutableStateOf<ControlAction?>(null) }
    var lastCode by remember { mutableStateOf<Int?>(null) }
    var currentPads by remember { mutableStateOf(pads) }

    fun store(newBindings: Map<ControlAction, Int>, newStick: Boolean) {
        bindings = newBindings
        stick = newStick
        settings.controlBindings = newBindings
        settings.stickEnabled = newStick
        settings.writeControls(newBindings, newStick, currentPads.isNotEmpty())
    }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { currentPads = connectedPads(context) }
            override fun onInputDeviceRemoved(deviceId: Int) { currentPads = connectedPads(context) }
            override fun onInputDeviceChanged(deviceId: Int) { currentPads = connectedPads(context) }
        }
        manager.registerInputDeviceListener(listener, null)
        onDispose { manager.unregisterInputDeviceListener(listener) }
    }

    // La captura va enganchada a la vista raíz: así recibe las teclas del mando
    // sin depender de que un composable diminuto tenga el foco.
    DisposableEffect(view, capturing) {
        if (capturing == null) return@DisposableEffect onDispose { }
        val handler = ViewKeyHandler(
            onKey = { code ->
                lastCode = code
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                val action = capturing
                if (action != null) {
                    store(bindings + (action to code), stick)
                    capturing = null
                }
            },
        )
        view.setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handler.handle(event.keyCode)
                true
            } else {
                true
            }
        }
        view.requestFocus()
        onDispose { view.setOnKeyListener(null) }
    }

    capturing?.let { action ->
        AlertDialog(
            onDismissRequest = { capturing = null },
            title = { Text("Asignar ${action.key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pulsa el botón del mando que quieras para «${action.hint}».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "También puedes tocar cualquier botón del mando y luego elegir la acción desde la lista.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { capturing = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controles") },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pulsa un botón del mando",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Después toca la acción a la que quieres asignarlo. Si el mando no aparece, conéctalo y espera un momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (lastCode != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (lastCode != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                lastCode?.let { buttonName(it) } ?: "Esperando…",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when {
                                    lastCode == null -> "El último botón pulsado aparecerá aquí"
                                    else -> actionFor(bindings, lastCode!!)?.let { "Asignado a ${it.key}" } ?: "Sin asignar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (currentPads.isEmpty()) {
                    Text(
                        "No hay mando conectado. Puedes usar el teclado si tu mando lo simula.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        currentPads.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ControlsSection("Botones") {
                ControlAction.entries.groupBy { it.group }.forEach { (group, actions) ->
                    Text(
                        group,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
                    )
                    actions.forEach { action ->
                        val code = bindings[action] ?: action.defaultCode
                        ControlRow(
                            action = action,
                            code = code,
                            onClick = { capturing = action },
                        )
                    }
                }
            }

            ControlsSection("Movimiento") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mover con el stick", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "El stick izquierdo mueve al personaje",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = stick, onCheckedChange = { store(bindings, it) })
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { store(emptyMap(), true) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restaurar los valores por defecto")
                }
                Text(
                    "Los cambios se aplican la próxima vez que abras el juego.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private class ViewKeyHandler(val onKey: (Int) -> Unit) {
    fun handle(code: Int) = onKey(code)
}

@Composable
private fun ControlRow(action: ControlAction, code: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                action.hint,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tecla ${action.key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                buttonName(code),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ControlsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column { content() }
        }
    }
}

private fun connectedPads(context: Context): List<String> {
    val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    val names = mutableListOf<String>()
    for (id in manager.inputDeviceIds) {
        val device = manager.getInputDevice(id) ?: continue
        val gamepad = device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!device.isVirtual && (gamepad || joystick)) names.add(device.name)
    }
    return names
}
