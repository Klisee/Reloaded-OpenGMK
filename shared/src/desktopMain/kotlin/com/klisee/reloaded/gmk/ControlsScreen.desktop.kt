package com.klisee.reloaded.gmk

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Códigos vk de Windows/Game Maker que usa el motor para las teclas del juego. */
private val keyOptions = listOf(
    "A" to 65, "B" to 66, "C" to 67, "D" to 68, "E" to 69, "F" to 70, "G" to 71,
    "H" to 72, "I" to 73, "J" to 74, "K" to 75, "L" to 76, "M" to 77, "N" to 78,
    "O" to 79, "P" to 80, "Q" to 81, "R" to 82, "S" to 83, "T" to 84, "U" to 85,
    "V" to 86, "W" to 87, "X" to 88, "Y" to 89, "Z" to 90,
    "Espacio" to 32, "Enter" to 13, "Esc" to 27, "Tab" to 9, "Retroceso" to 8,
    "Flecha arriba" to 38, "Flecha abajo" to 40, "Flecha izquierda" to 37, "Flecha derecha" to 39,
    "Mayús izq." to 16, "Ctrl izq." to 17, "Alt izq." to 18,
    "0" to 48, "1" to 49, "2" to 50, "3" to 51, "4" to 52,
    "5" to 53, "6" to 54, "7" to 55, "8" to 56, "9" to 57,
)

private fun keyLabel(code: Int): String =
    keyOptions.firstOrNull { it.second == code }?.first ?: "Código $code"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ControlsScreen(settings: SettingsStore, pads: List<String>, onBack: () -> Unit) {
    var bindings by remember { mutableStateOf(settings.controlBindings) }
    var stick by remember { mutableStateOf(settings.stickEnabled) }
    var editing by remember { mutableStateOf<ControlAction?>(null) }
    var editedCode by remember { mutableStateOf<Int?>(null) }

    fun store(newBindings: Map<ControlAction, Int>, newStick: Boolean) {
        bindings = newBindings
        stick = newStick
        settings.controlBindings = newBindings
        settings.stickEnabled = newStick
        settings.writeControls(newBindings, newStick, pads.isNotEmpty())
    }

    editing?.let { action ->
        val current = editedCode ?: bindings[action] ?: action.engineKeyCode
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Asignar tecla para ${action.hint}") },
            text = {
                Column(
                    Modifier.height(380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    keyOptions.forEach { (label, code) ->
                        Surface(
                            onClick = { editedCode = code },
                            color = if (code == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (code == current) {
                                    Text(
                                        "Actual",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = editedCode ?: current
                        store(bindings + (action to chosen), stick)
                        editedCode = null
                        editing = null
                    },
                ) { Text("Asignar") }
            },
            dismissButton = { TextButton(onClick = { editing = null; editedCode = null }) { Text("Cancelar") } },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Elige la tecla que quieres para cada acción del juego. Se guarda en la carpeta del juego y se aplica al abrirlo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ControlsDesktopSection("Acciones") {
                ControlAction.entries.groupBy { it.group }.forEach { (group, actions) ->
                    Text(
                        group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
                    )
                    actions.forEach { action ->
                        val code = bindings[action] ?: action.engineKeyCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editedCode = null
                                    editing = action
                                }
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
                                    "Se juega con ${keyLabel(code)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    keyLabel(code),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            ControlsDesktopSection("Movimiento") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mover con el mando", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "En escritorio el motor ya lee los mandos conectados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = stick, onCheckedChange = { store(bindings, it) })
                }
            }

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
    }
}

@Composable
private fun ControlsDesktopSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}
