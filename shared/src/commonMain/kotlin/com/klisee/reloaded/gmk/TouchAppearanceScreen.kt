package com.klisee.reloaded.gmk

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchAppearanceScreen(settings: SettingsStore, onBack: () -> Unit) {
    var appearance by remember { mutableStateOf(settings.touchAppearance) }
    var customHex by remember { mutableStateOf(appearance.colorHex.takeUnless { it == "original" }.orEmpty()) }

    fun update(next: TouchAppearance) {
        appearance = next
        settings.touchAppearance = next
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Apariencia de controles", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Se guarda automáticamente y se aplica al iniciar el juego. La opacidad se mantiene en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppearancePreview(appearance)
            Text("Estilo gráfico", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TouchStyle.entries.forEach { style ->
                    FilterChip(
                        selected = appearance.style == style,
                        onClick = { update(appearance.copy(style = style)) },
                        label = { Text(style.title) },
                    )
                }
            }
            Text(
                "Moderno usa los sprites semitransparentes; Clásico, los botones suaves con anillos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Color de los botones", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = appearance.tint == null,
                    onClick = { customHex = ""; update(appearance.copy(colorHex = "original")) },
                    label = { Text("Original") },
                )
                listOf(
                    "63E0CA" to "Menta", "59A8FF" to "Azul", "FF786E" to "Rojo",
                    "FFD166" to "Dorado", "C69CFF" to "Violeta", "FFFFFF" to "Blanco",
                ).forEach { (hex, label) ->
                    FilterChip(
                        selected = appearance.colorHex == hex,
                        onClick = { customHex = hex; update(appearance.copy(colorHex = hex)) },
                        label = { Text(label) },
                        leadingIcon = {
                            Box(Modifier.size(16.dp).background(Color(0xFF000000 or hex.toLong(16)), CircleShape))
                        },
                    )
                }
            }
            OutlinedTextField(
                value = customHex,
                onValueChange = { raw ->
                    customHex = raw.filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                    if (customHex.length == 6) update(appearance.copy(colorHex = customHex))
                },
                label = { Text("Color personalizado · HEX") },
                prefix = { Text("#") },
                singleLine = true,
                supportingText = { Text("Seis caracteres, por ejemplo 63E0CA") },
                modifier = Modifier.fillMaxWidth(),
            )
            AppearanceToggle(
                title = "Animaciones al pulsar",
                description = "Reducción y cambio de brillo rápidos al tocar.",
                checked = appearance.animations,
                onChange = { update(appearance.copy(animations = it)) },
            )
            AppearanceToggle(
                title = "Vibración táctil",
                description = "Un pulso breve al presionar. Respeta la respuesta táctil de Android.",
                checked = appearance.haptics,
                onChange = { update(appearance.copy(haptics = it)) },
            )
            Text("Intensidad", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchStrength.entries.forEach { strength ->
                    FilterChip(
                        selected = appearance.strength == strength,
                        enabled = appearance.haptics,
                        onClick = { update(appearance.copy(strength = strength)) },
                        label = { Text(strength.title) },
                    )
                }
            }
            TextButton(
                onClick = {
                    customHex = ""
                    update(TouchAppearance())
                },
            ) { Text("Restablecer apariencia y respuesta") }
        }
    }
}

@Composable
private fun AppearancePreview(appearance: TouchAppearance) {
    val tint = appearance.tint?.let { Color(0xFF000000 or it.toLong()) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF131B25),
        modifier = Modifier.fillMaxWidth().height(140.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Z", "X", "1", "B").forEach { label ->
                Box(
                    modifier = Modifier.size(58.dp).background((tint ?: Color(0xFF59A8FF)).copy(alpha = .55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun AppearanceToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
