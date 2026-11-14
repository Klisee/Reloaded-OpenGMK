package com.klisee.reloaded.gmk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()

actual fun formatDate(millis: Long): String = millis.toString()

actual suspend fun latestBlogPost(): Result<BlogPost?> = Result.success(null)

actual suspend fun checkForUpdate(installed: String, abis: List<String>): Result<UpdateRelease?> =
    Result.success(null)

actual fun readLog(repo: GameStore): String = "Los logs de iOS se muestran desde el host nativo."

@Composable
actual fun ControlsScreen(settings: SettingsStore, pads: List<String>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Mando", style = MaterialTheme.typography.headlineSmall)
        Text("La configuración de mandos de iOS se conectará al motor.")
        TextButton(onClick = onBack) { Text("Atrás") }
    }
}

@Composable
actual fun TouchAssetsScreen(
    settings: SettingsStore,
    onPickImage: ((PickedFile?) -> Unit) -> Unit,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sprites y paquetes", style = MaterialTheme.typography.headlineSmall)
        Text("La edición de sprites de iOS se conectará al motor.")
        TextButton(onClick = onBack) { Text("Atrás") }
    }
}

actual fun platformLabel(): String = "iOS"

actual fun systemLanguage(): String = "es"


@androidx.compose.runtime.Composable
actual fun rememberInterfaceHaptics(): InterfaceHaptics =
    androidx.compose.runtime.remember {
        InterfaceHaptics(onTick = {}, onConfirm = {})
    }
