package com.klisee.reloaded.gmk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun TouchAssetsScreen(
    settings: SettingsStore,
    onPickImage: ((PickedFile?) -> Unit) -> Unit,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val appSettings = settings as AppSettings
    var profile by remember { mutableStateOf(TouchProfile.WORLD) }
    var selected by remember { mutableStateOf("z") }
    var profileMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val stored = appSettings.touchSprites
    val key = TouchSpriteCatalog.storageKey(selected, profile)
    val effective = stored[key] ?: stored[selected]
    val customFile = effective?.takeIf { it != "original" }?.let(appSettings::spriteFile)
    val target = TouchSpriteCatalog.target(selected)

    fun choose() {
        onPickImage { file ->
            if (file == null) return@onPickImage
            runCatching {
                appSettings.stageSprite(key, file.readBytes())
                message = "Sprite guardado. Se aplicará al iniciar el juego."
            }.onFailure { message = it.message ?: "No pude leer el PNG." }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Sprites y paquetes", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Sprites personalizados", style = MaterialTheme.typography.titleLarge)
            Text(
                "Elige un PNG para cada botón o para la base y la palanca del joystick. Se guarda en la app y se aplica al iniciar el juego.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Perfil: ${profile.title}")
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    listOf(TouchProfile.WORLD, TouchProfile.BATTLE).forEach { item ->
                        DropdownMenuItem(text = { Text(item.title) }, onClick = {
                            profile = item
                            profileMenu = false
                        })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(target.title)
                }
                DropdownMenu(
                    expanded = targetMenu,
                    onDismissRequest = { targetMenu = false },
                    modifier = Modifier.heightIn(max = 300.dp),
                ) {
                    TouchSpriteCatalog.targets.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(item.title + if (TouchSpriteCatalog.storageKey(item.id, profile) in stored) " · personalizado" else "")
                            },
                            onClick = { selected = item.id; targetMenu = false },
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (customFile != null && customFile.isFile) {
                        AsyncImage(
                            model = customFile,
                            contentDescription = target.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Text("Apariencia original", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                if (customFile != null) "Sprite personalizado" else "Apariencia original del control",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::choose, modifier = Modifier.weight(1f)) { Text("Elegir PNG") }
                OutlinedButton(
                    onClick = {
                        appSettings.resetSprite(key)
                        message = "Sprite original restaurado."
                    },
                    enabled = effective != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Restaurar original") }
            }
            Text(
                "Hasta 8 MB. Se conserva la proporción. A, M, E, P, L, T y PKM tienen sprites independientes en Exploración y Combate. Los demás comparten imagen. No cambia su función ni zona táctil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    appSettings.clearSprites()
                    message = "Sprites originales restaurados."
                },
                enabled = stored.isNotEmpty(),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Restaurar todos los sprites")
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Text("Importar y exportar controles", style = MaterialTheme.typography.titleLarge)
            Text(
                "El ZIP incluye posiciones, tamaños, visibilidad, estilo, color, opacidad y sprites. Las partidas no forman parte del paquete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onPickZip { file ->
                            if (file == null) return@onPickZip
                            runCatching {
                                TouchPack.apply(appSettings, TouchPack.read(file.requireJvmFile()), applySettings = true, applySprites = true)
                                message = "Controles importados."
                            }.onFailure { message = it.message ?: "No pude importar el paquete." }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Importar paquete") }
                Button(
                    onClick = {
                        runCatching {
                            val file = TouchPack.export(appSettings, includeSprites = true)
                            message = "Paquete exportado en ${file.absolutePath}"
                        }.onFailure { message = it.message ?: "No pude exportar el paquete." }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Exportar paquete") }
            }
        }
    }
}
