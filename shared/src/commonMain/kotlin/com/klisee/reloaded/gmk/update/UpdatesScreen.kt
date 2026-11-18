package com.klisee.reloaded.gmk.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pantalla de actualizaciones: estado de la comprobación, botón de descargar/instalar, progreso,
 * historial de versiones con sus notas y los ajustes del canal.
 *
 * Es la misma en las tres plataformas; lo que cambia (de dónde salen las releases, cómo se descarga
 * y cómo se abre el instalador) vive detrás de [UpdateManager].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    manager: UpdateManager,
    settings: UpdateSettings,
    onBack: (() -> Unit)? = null,
    onOpenUrl: (String) -> Unit = {},
) {
    val state by manager.state.collectAsState()
    val history by manager.history.collectAsState()
    val progress by manager.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<GitHubRelease?>(null) }
    var autoCheck by remember { mutableStateOf(settings.autoCheck) }
    var channel by remember { mutableStateOf(settings.channel) }

    val cachedHistory = remember { manager.cachedHistory() }
    val shownHistory = history.ifEmpty { cachedHistory }
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    // Primera entrada: si nunca se comprobó, se comprueba ahora para no enseñar una pantalla vacía.
    LaunchedEffect(Unit) {
        if (settings.lastCheckAt == 0L || state is UpdateState.Idle) manager.check()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actualizaciones") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                actions = {
                    IconButton(enabled = !busy, onClick = { scope.launch { manager.check() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Buscar de nuevo")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VersionTiles(
                version = manager.currentVersion,
                lastCheckAt = settings.lastCheckAt,
                platform = manager.platform,
            )

            StatusCard(
                state = state,
                platform = manager.platform,
                progress = progress,
                onDownload = {
                    val release = (state as? UpdateState.Available)?.release ?: return@StatusCard
                    scope.launch { manager.download(release) }
                },
                onInstall = {
                    val ready = state as? UpdateState.Ready ?: return@StatusCard
                    scope.launch { manager.install(ready) }
                },
                onRetry = { scope.launch { manager.check() } },
                onOpenPage = { onOpenUrl(UPDATE_RELEASES_PAGE) },
            )

            SectionCard("Ajustes") {
                SettingsRow(
                    title = "Buscar actualizaciones automáticamente",
                    subtitle = "Al abrir la app, como mucho una vez por hora",
                ) {
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = {
                            autoCheck = it
                            settings.autoCheck = it
                            if (it) scope.launch { manager.check(automatic = true) }
                        },
                    )
                }
                Divider()
                SettingsRow(
                    title = "Versiones de prueba",
                    subtitle = "Incluye betas publicadas antes que la estable",
                ) {
                    Switch(
                        checked = channel == UPDATE_CHANNEL_BETA,
                        onCheckedChange = {
                            channel = if (it) UPDATE_CHANNEL_BETA else UPDATE_CHANNEL_STABLE
                            settings.channel = channel
                            scope.launch { manager.check() }
                        },
                    )
                }
            }

            if (shownHistory.isNotEmpty()) {
                SectionCard("Versiones anteriores") {
                    shownHistory.forEachIndexed { index, release ->
                        if (index > 0) Divider()
                        HistoryRow(
                            release = release,
                            current = release.version == manager.currentVersion,
                            onClick = { expanded = release },
                        )
                    }
                }
            }

            TextButton(onClick = { onOpenUrl(UPDATE_RELEASES_PAGE) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ver todas las versiones en GitHub")
            }
        }
    }

    expanded?.let { release ->
        ReleaseDialog(
            release = release,
            onDismiss = { expanded = null },
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun VersionTiles(version: String, lastCheckAt: Long, platform: UpdatePlatform) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Bloque superior redondeado solo arriba, como una tarjeta que se abre hacia abajo.
        Surface(
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Versión instalada", style = MaterialTheme.typography.bodyLarge)
                Text(
                    version,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Tile(
                label = "Última comprobación",
                value = if (lastCheckAt <= 0L) "Nunca" else formatShortDate(lastCheckAt),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 4.dp),
            )
            Tile(
                label = "Paquete",
                value = platform.packageLabel(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 24.dp),
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    modifier: Modifier,
    shape: RoundedCornerShape,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer, shape = shape, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: UpdateState,
    platform: UpdatePlatform,
    progress: Int?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                is UpdateState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Column {
                            Text("Buscando actualizaciones…", style = MaterialTheme.typography.titleMedium)
                            Text("Consultando las versiones publicadas", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }

                is UpdateState.UpToDate -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                        Column {
                            Text("Tienes la última versión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("No hay nada nuevo por ahora", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }

                is UpdateState.Available -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.NewReleases, null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                        Column {
                            Text("Versión ${state.release.version} disponible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                notesPlainPreview(state.release.body.orEmpty()).ifBlank { "Pulsa para ver los cambios." },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.CloudDownload, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Descargar ${releaseSizeLabel(state.release, platform)}".trim())
                    }
                }

                is UpdateState.Downloading -> {
                    Text("Descargando ${state.release.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    WavyProgress(progress ?: state.percent)
                    Text(
                        "${progress ?: state.percent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                is UpdateState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.SystemUpdate, null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                        Column {
                            Text(
                                if (state.installing) "Abriendo el instalador…" else "Listo para instalar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "El archivo se verificó con su SHA-256",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = onInstall, enabled = !state.installing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("Instalar ${state.release.version}")
                    }
                }

                is UpdateState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = scheme.error, modifier = Modifier.size(26.dp))
                        Column {
                            Text("No se pudo actualizar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.error)
                            Text(state.reason.message(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, shape = RoundedCornerShape(16.dp)) { Text("Reintentar") }
                        TextButton(onClick = onOpenPage) { Text("Abrir en GitHub") }
                    }
                }

                is UpdateState.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Refresh, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                        Text("Sin comprobar todavía", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = onRetry, shape = RoundedCornerShape(16.dp)) { Text("Buscar actualizaciones") }
                }
            }
        }
    }
}

/**
 * Barra de progreso con un brillo que la recorre. Es solo decorativa: el avance real lo da [percent],
 * pero el movimiento continuo hace que una descarga lenta no parezca colgada.
 */
@Composable
private fun WavyProgress(percent: Int) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "update-progress")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "update-shimmer",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary),
        )
        // Reflejo que cruza la parte ya descargada, para que se vea viva.
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(8.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        startX = -200f + shimmer * 900f,
                        endX = shimmer * 900f,
                    ),
                ),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun HistoryRow(release: GitHubRelease, current: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(release.version, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (current) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(percent = 50), color = scheme.primary.copy(alpha = 0.16f)) {
                        Text(
                            "instalada",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (release.prerelease) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(percent = 50), color = scheme.tertiaryContainer) {
                        Text(
                            "beta",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                notesPlainPreview(release.body.orEmpty(), 90).ifBlank { "Sin notas" },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        release.publishedAt?.take(10)?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReleaseDialog(release: GitHubRelease, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val blocks = remember(release.version) { parseReleaseNotes(release.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novedades de ${release.version}") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                release.publishedAt?.let {
                    Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (blocks.isEmpty()) {
                    Text("Esta versión no trae notas.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    blocks.forEach { block -> NoteBlockView(block, onOpenUrl) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = {
            TextButton(onClick = { onOpenUrl(release.htmlUrl.ifBlank { UPDATE_RELEASES_PAGE }) }) {
                Text("Ver en GitHub")
            }
        },
    )
}

@Composable
private fun NoteBlockView(block: NoteBlock, onOpenUrl: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (block) {
        is NoteBlock.Heading -> Text(
            block.text,
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        is NoteBlock.Paragraph -> Text(
            buildAnnotatedStringFrom(block.spans),
            style = MaterialTheme.typography.bodyMedium,
        )

        is NoteBlock.Bullet -> Row(Modifier.padding(start = (block.indent * 14).dp)) {
            Text(
                (block.marker?.plus(".") ?: "•") + " ",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
            )
            Text(buildAnnotatedStringFrom(block.spans), style = MaterialTheme.typography.bodyMedium)
        }

        is NoteBlock.Code -> Surface(
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                block.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(10.dp),
            )
        }

        is NoteBlock.Divider -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(1.dp)
                .background(scheme.outlineVariant),
        )
    }
}

/** Convierte los trozos con estilo a un `AnnotatedString` de Compose. */
private fun buildAnnotatedStringFrom(spans: List<NoteSpan>) = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is NoteSpan.Text -> append(span.value)
            is NoteSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.value) }
            is NoteSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.value) }
            is NoteSpan.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x22808080),
                ),
            ) { append(span.value) }
            is NoteSpan.Link -> withStyle(
                SpanStyle(
                    color = Color(0xFF5AA9FF),
                    textDecoration = TextDecoration.Underline,
                ),
            ) { append(span.value) }
        }
    }
}

private fun UpdatePlatform.packageLabel(): String = when (this) {
    UpdatePlatform.Windows -> "MSI"
    UpdatePlatform.LinuxDeb -> "DEB"
    UpdatePlatform.LinuxAppImage -> "AppImage"
    UpdatePlatform.AndroidArm64 -> "APK"
}

private fun releaseSizeLabel(release: GitHubRelease, platform: UpdatePlatform): String {
    val asset = release.assetFor(platform) ?: return ""
    if (asset.size <= 0) return ""
    val megabytes = asset.size / (1024.0 * 1024.0)
    return "(${((megabytes * 10).toInt() / 10.0)} MB)"
}

/** Fecha corta y sin dependencias de formato por plataforma. */
expect fun formatShortDate(millis: Long): String
