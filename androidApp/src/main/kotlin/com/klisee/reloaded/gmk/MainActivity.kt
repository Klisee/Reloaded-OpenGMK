package com.klisee.reloaded.gmk

import com.klisee.reloaded.GameActivity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private fun connectedPads(): List<String> {
        val manager = getSystemService(android.hardware.input.InputManager::class.java) ?: return emptyList()
        val names = mutableListOf<String>()
        for (id in manager.inputDeviceIds) {
            val device = manager.getInputDevice(id) ?: continue
            val gamepad = device.sources and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD
            val joystick = device.sources and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK
            if (!device.isVirtual && (gamepad || joystick)) names.add(device.name)
        }
        return names
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LegacyMigration.migrate(this, filesDir)
        enableEdgeToEdge()
        setContent {
            val gameRepository = remember { GameRepository(filesDir) }
            val repo = remember { JvmGameStore(gameRepository) }
            val settings = remember { AppSettings(filesDir) }
            val onlineEngine = remember { JvmOnlineEngineStore(gameRepository.gameDir) }
            val appVersion = remember {
                runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                    .getOrNull().orEmpty().ifEmpty { "0.5.0-dev" }
            }
            val updateManager = remember {
                com.klisee.reloaded.gmk.update.UpdateManager(
                    bridge = com.klisee.reloaded.gmk.update.AndroidUpdateBridge(applicationContext, appVersion),
                    settings = com.klisee.reloaded.gmk.update.FileUpdateSettings(filesDir),
                    userAgent = "Reloaded-OpenGMK/$appVersion",
                ).apply {
                    // Aviso del sistema cuando el chequeo automático ve algo nuevo.
                    onUpdateFound = { release ->
                        com.klisee.reloaded.gmk.update.AndroidUpdateNotifier.notifyAvailable(
                            applicationContext,
                            release.version,
                        )
                    }
                }
            }
            var pending by remember { mutableStateOf<((PickedFile?) -> Unit)?>(null) }
            var pendingImage by remember { mutableStateOf<((PickedFile?) -> Unit)?>(null) }
            var pendingSave by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
            var network by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
            }
            val requestNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { network = it }
            val notificationsPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            val scope = rememberCoroutineScope()
            val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                val data = pendingSave
                pendingSave = null
                if (uri != null && data != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                contentResolver.openOutputStream(uri)?.use { it.write(data.second) }
                            }
                        }
                    }
                }
            }
            val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val callback = pendingImage
                pendingImage = null
                if (uri == null) {
                    callback?.invoke(null)
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            val target = File(cacheDir, "sprite.png")
                            contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("No pude abrir la imagen.")
                            target
                        }.getOrNull()
                    }
                    callback?.invoke(file?.let(::FilePickedFile))
                }
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val callback = pending
                pending = null
                if (uri == null) {
                    callback?.invoke(null)
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        AndroidZipPicker.copyToCache(applicationContext, uri) { line ->
                            gameRepository.log(line)
                        }
                    }
                    result.onFailure { error ->
                        // Sin esto el selector se cerraba en silencio y parecía que el botón no hacía nada.
                        runCatching {
                            android.widget.Toast.makeText(
                                applicationContext,
                                error.message ?: "No pude preparar el zip para importar.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                        gameRepository.log(
                            "Selector: fallo al preparar el zip: " +
                                "${error.javaClass.simpleName}: ${error.message}",
                        )
                    }
                    callback?.invoke(result.getOrNull()?.let(::FilePickedFile))
                }
            }
            LaunchedEffect(Unit) {
                if (!network) requestNetwork.launch(Manifest.permission.INTERNET)
                // Android 13+ pide permiso explícito para notificar; sin él no habría aviso de updates.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                repo.prepare()
                EngineConfig.copyAssets(filesDir, assets)
            }
            val fontFamily = FontFamily(
                Font(R.font.google_sans_flex, FontWeight.Normal),
                Font(R.font.google_sans_flex, FontWeight.Medium),
                Font(R.font.google_sans_flex, FontWeight.SemiBold),
                Font(R.font.google_sans_flex, FontWeight.Bold),
            )
            App(
                repo = repo,
                settings = settings,
                fontFamily = fontFamily,
                onPickZip = { callback ->
                    pending = callback
                    // Algunos gestores de archivos anuncian el zip con MIME propio; con varios
                    // tipos el selector deja elegirlo en más dispositivos.
                    picker.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/x-zip",
                            "application/octet-stream",
                        ),
                    )
                },
                onPickImage = { callback ->
                    pendingImage = callback
                    imagePicker.launch(arrayOf("image/png"))
                },
                onSaveFile = { name, bytes ->
                    pendingSave = name to bytes
                    saveFile.launch(name)
                },
                onLaunch = {
                    EngineConfig.write(
                        filesDir,
                        settings,
                        assets,
                        connectedPads().isNotEmpty(),
                        runCatching { windowManager.defaultDisplay.refreshRate }.getOrDefault(60f),
                    )
                    EngineConfig.copyAssets(filesDir, assets)
                    if (onlineEngine.load().enabled) {
                        runCatching {
                            val backups = SaveBackupManager(filesDir)
                            backups.create("pre-online")
                            backups.prune()
                        }
                    }
                    startActivity(android.content.Intent(this, GameActivity::class.java))
                },
                onOpenUrl = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                onShareLogs = { text ->
                    runCatching {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                "Compartir logs",
                            ),
                        )
                    }
                },
                networkGranted = network,
                onRequestNetwork = { requestNetwork.launch(Manifest.permission.INTERNET) },
                supportedAbis = android.os.Build.SUPPORTED_ABIS.toList(),
                connectedPads = connectedPads(),
                refreshRate = runCatching { windowManager.defaultDisplay.refreshRate }.getOrDefault(60f),
                appVersion = appVersion,
                logo = painterResource(R.drawable.reloaded_logo),
                onlineEngine = onlineEngine,
                updateManager = updateManager,
            )
        }
    }
}
