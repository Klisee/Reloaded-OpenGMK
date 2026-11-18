package com.klisee.reloaded.gmk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidSettingsScreen(
    repo: GameStore,
    settings: SettingsStore,
    revision: Int,
    appVersion: String,
    theme: String,
    onThemeChange: (String) -> Unit,
    darkMode: String,
    onDarkModeChange: (String) -> Unit,
    onClose: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit,
    onSaves: () -> Unit,
    onMods: () -> Unit,
    onTouch: () -> Unit,
    onControls: () -> Unit,
    onFps: () -> Unit,
    onUpdates: (() -> Unit)? = null,
    onChanged: () -> Unit,
) {
    var status by remember(revision) { mutableStateOf(repo.status()) }
    val installed = status as? GameStatus.Ready
    var language by remember { mutableStateOf(settings.language) }
    var showLanguage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text("Idioma del juego") },
            text = {
                Column {
                    listOf(
                        "auto" to "Automático",
                        "es" to "Español",
                        "en" to "English",
                    ).forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                language = value
                                settings.language = value
                                showLanguage = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            if (language == value) Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text("Cerrar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar el juego") },
            text = { Text("Se borrará la copia extraída (${formatBytes(installed?.sizeBytes ?: 0)}). Tus partidas guardadas también se perderán.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.deleteGame()
                        status = repo.status()
                        onChanged()
                    }
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Cerrar", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = reloadedTopBarColors(),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            LiquidGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CatchingPokemon, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Reloaded OpenGMK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "OpenGMK · v$appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                LiquidRow(
                    icon = Icons.Filled.Palette,
                    title = "Cambiar tema",
                    subtitle = if (theme == "liquid") "Liquid Glass" else "Material You",
                    onClick = { onThemeChange(if (theme == "liquid") "material" else "liquid") },
                )
            }

            LiquidGroup(title = "Juego") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LiquidStat("Versión", installed?.version ?: "—", Modifier.weight(1f))
                    LiquidStat("Tamaño", installed?.let { formatBytes(it.sizeBytes) } ?: "—", Modifier.weight(1f))
                    LiquidStat("Instalado", installed?.let { formatDate(it.installedAt) } ?: "—", Modifier.weight(1f))
                }
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(Icons.Filled.Build, "Gestionar mods", "Activa o desactiva los mods instalados", onClick = onMods)
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(
                    Icons.Filled.Save,
                    "Mis partidas",
                    if (installed == null) "El juego no está instalado" else "Consulta, importa y exporta tus partidas",
                    enabled = installed != null,
                    onClick = onSaves,
                )
                if (installed != null) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    LiquidRow(
                        Icons.Filled.Delete,
                        "Borrar el juego",
                        "Libera ${formatBytes(installed.sizeBytes)}",
                        danger = true,
                        onClick = { confirmDelete = true },
                    )
                }
            }

            LiquidGroup(title = "General") {
                LiquidRow(
                    Icons.Filled.Language,
                    "Idioma",
                    when (language) {
                        "es" -> "Español"
                        "en" -> "English"
                        else -> "Automático"
                    },
                    onClick = { showLanguage = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(Icons.Filled.VideogameAsset, "Controles táctiles", "Estilo, movimiento y editores", onClick = onTouch)
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(Icons.Filled.SportsEsports, "Mando", "Botones y prueba del mando", onClick = onControls)
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(Icons.Filled.Speed, "FPS y pantalla", "Framerate, aspecto y contador", onClick = onFps)
            }

            LiquidGroup(title = "Apariencia") {
                LiquidRow(
                    Icons.Filled.DarkMode,
                    "Modo oscuro",
                    when (darkMode) {
                        "dark" -> "Siempre oscuro"
                        "light" -> "Siempre claro"
                        else -> "Sigue al sistema"
                    },
                    selected = darkMode == "auto",
                    onClick = { onDarkModeChange("auto") },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(
                    Icons.Filled.LightMode,
                    "Modo claro forzado",
                    "Útil si el sistema no informa el tema",
                    selected = darkMode == "light",
                    onClick = { onDarkModeChange("light") },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(
                    Icons.Filled.Nightlight,
                    "Modo oscuro forzado",
                    "Negro y azul, como Windows 11",
                    selected = darkMode == "dark",
                    onClick = { onDarkModeChange("dark") },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(
                    Icons.Filled.Palette,
                    "Material You",
                    "Colores del sistema",
                    selected = theme == "material",
                    onClick = { onThemeChange("material") },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(
                    Icons.Filled.CatchingPokemon,
                    "Liquid Glass",
                    "Cristal neutro adaptativo",
                    selected = theme == "liquid",
                    onClick = { onThemeChange("liquid") },
                )
            }

            LiquidGroup(title = "Ayuda") {
                if (onUpdates != null) {
                    LiquidRow(
                        Icons.Filled.SystemUpdate,
                        "Actualizaciones",
                        "Busca, descarga e instala versiones nuevas",
                        onClick = onUpdates,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
                LiquidRow(Icons.Filled.Article, "Logs", "Comparte los logs si algo falla", onClick = onLogs)
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LiquidRow(Icons.Filled.Info, "Acerca de", "Versión, créditos y aviso", onClick = onAbout)
            }

            Spacer(Modifier.height(BottomNavSpace))
        }
    }
}

@Composable
private fun LiquidGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun LiquidRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = when {
        danger -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        } else if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LiquidStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
