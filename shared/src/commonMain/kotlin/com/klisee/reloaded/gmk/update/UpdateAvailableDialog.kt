package com.klisee.reloaded.gmk.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Aviso de actualización dentro de la app.
 *
 * Se abre solo al arrancar cuando hay una versión nueva, sin notificaciones del sistema: el usuario
 * ya está mirando la app, así que un diálogo es menos intrusivo y no depende de permisos.
 *
 * Tiene tres caras: disponible (Instalar / Después), descargando (con el avance) y error (con el
 * motivo y un botón para reintentar abriendo la pantalla de actualizaciones).
 */
@Composable
fun UpdateAvailableDialog(
    release: GitHubRelease,
    state: UpdateState,
    progress: Int?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    logo: Painter? = null,
) {
    val downloading = state is UpdateState.Downloading
    val failed = state as? UpdateState.Failed
    val ready = state as? UpdateState.Ready

    AlertDialog(
        // Mientras descarga no se deja cerrar de golpe: se puede ocultar con el botón.
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                when {
                    downloading -> "Descargando actualización"
                    ready != null -> "Lista para instalar"
                    failed != null -> "No se pudo actualizar"
                    else -> "Actualización disponible"
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (logo != null) {
                    Image(
                        painter = logo,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    "Versión ${release.version}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    downloading -> {
                        Text("Descargando el paquete verificado con su SHA-256.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        val percent = progress ?: (state as UpdateState.Downloading).percent
                        LinearProgressIndicator(
                            progress = { percent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    ready != null -> {
                        Text(
                            if (ready.installing) "Abriendo el instalador del sistema…"
                            else "El paquete se descargó y se verificó correctamente.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    failed != null -> {
                        Text(failed.reason.message(), style = MaterialTheme.typography.bodyMedium)
                    }

                    else -> {
                        // Las notas de la release, con el mismo parser que la pantalla de ajustes.
                        val notes = notesPlainPreview(release.body.orEmpty(), 320)
                        if (notes.isNotBlank()) {
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            "Se descargará el paquete y se abrirá el instalador; el sistema pedirá confirmar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloading -> OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) {
                    Text("Ocultar")
                }
                ready != null -> Button(onClick = onInstall, shape = RoundedCornerShape(16.dp)) {
                    Text("Instalar", fontWeight = FontWeight.Bold)
                }
                failed != null -> Button(
                    onClick = { onOpenUrl(UPDATE_RELEASES_PAGE) },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Ver en GitHub")
                }
                else -> Button(onClick = onInstall, shape = RoundedCornerShape(16.dp)) {
                    Text("Instalar", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!downloading && ready == null) {
                TextButton(onClick = onDismiss) { Text("Después") }
            }
        },
    )
}
