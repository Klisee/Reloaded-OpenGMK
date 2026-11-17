package com.klisee.reloaded.gmk

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.Colors
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.launch
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private fun isWindows(): Boolean =
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

@OptIn(ExperimentalFluentApi::class)
fun runWindowsApplication(root: File) {
    val jvmRepo = GameRepository(root)
    val repo = JvmGameStore(jvmRepo)
    val settings = AppSettings(root)
    val onlineEngine = JvmOnlineEngineStore(jvmRepo.gameDir)
    repo.prepare()

    // FileKit necesita saber quién es la app y dónde guardar sus temporales; sin esto los
    // diálogos de archivo lanzan FileKitNotInitializedException y el botón "Importar" no hace nada.
    runCatching {
        FileKit.init(appId = "com.klisee.reloaded.opengmk", filesDir = root, cacheDir = File(root, "cache"))
    }

    @Composable
    fun AppContent(
        colorScheme: ColorScheme? = null,
        shapes: Shapes? = null,
        fontFamily: FontFamily = FontFamily.SansSerif,
        logo: Painter? = null,
        onLauncherClose: (() -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        App(
            repo = repo,
            settings = settings,
            onPickZip = { callback ->
                scope.launch {
                    val file = try {
                        FileKit.openFilePicker(type = FileKitType.File("zip"))
                    } catch (error: Throwable) {
                        java.io.File(root, "app.log").appendText(
                            "No se pudo abrir el selector de archivos: ${error.javaClass.simpleName}: ${error.message}\n",
                        )
                        null
                    }
                    callback(file?.file?.let(::FilePickedFile))
                }
            },
            onSaveFile = { name, bytes ->
                scope.launch {
                    val file = runCatching {
                        FileKit.openFileSaver(suggestedName = name)
                    }.getOrNull()
                    file?.file?.writeBytes(bytes)
                }
            },
            onPickImage = { callback ->
                scope.launch {
                    val file = runCatching {
                        FileKit.openFilePicker(type = FileKitType.File("png", "jpg", "jpeg"))
                    }.getOrNull()
                    callback(file?.file?.let(::FilePickedFile))
                }
            },
            onOpenUrl = { url ->
                runCatching {
                    if (isWindows()) {
                        ProcessBuilder("cmd", "/c", "start", "", url).start()
                    } else {
                        ProcessBuilder("xdg-open", url).start()
                    }
                }
            },
            onLaunch = {
                val exe = jvmRepo.gameExe()
                val engine = findEngine(root)
                val log = jvmRepo.logFile
                fun writeLog(text: String) = runCatching {
                    log.parentFile?.mkdirs()
                    log.appendText(text)
                }
                writeLog("\n=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} ===\n")
                when {
                    exe == null -> writeLog("No se encontró el ejecutable del juego en ${jvmRepo.gameDir}.\n")
                    engine == null -> writeLog(
                        "No se encontró el motor OpenGMK (${if (isWindows()) "gm8emulator.exe" else "gm8emulator"}).\n",
                    )
                    else -> {
                        if (onlineEngine.load().enabled) {
                            runCatching {
                                val backups = SaveBackupManager(root)
                                backups.create("pre-online")
                                backups.prune()
                            }.onFailure { writeLog("No se pudo respaldar las partidas: ${it.message}\n") }
                        }
                        writeLog("Ejecutando: ${engine.absolutePath} ${exe.absolutePath}\n")
                        val detectedHz = runCatching {
                            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                                .defaultScreenDevice.displayMode.refreshRate
                        }.getOrDefault(60).coerceAtLeast(60)
                        val renderMode = when (settings.fpsMode) {
                            "60" -> "60"
                            "120" -> "120"
                            "uncapped" -> detectedHz.toString()
                            else -> "native"
                        }
                        val backend = when (settings.renderer) {
                            "vulkan" -> "wgpu"
                            "opengl" -> "gl"
                            else -> null
                        }
                        writeLog("Video: render=$renderMode showFps=${settings.showFps} backend=${backend ?: "auto"}\n")
                        runCatching {
                            val builder = ProcessBuilder(engine.absolutePath, exe.absolutePath)
                                .directory(jvmRepo.gameDir)
                                .redirectErrorStream(true)
                                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                            builder.environment()["OPENGMK_RENDER"] = renderMode
                            if (backend != null) builder.environment()["OPENGMK_RENDERER"] = backend
                            builder.environment()["OPENGMK_SHOW_FPS"] = if (settings.showFps) "1" else "0"
                            builder.environment()["OPENGMK_SCALING"] = if (settings.keepAspect) "aspect" else "full"
                            builder.environment()["OPENGMK_RENDER_HZ"] = detectedHz.toString()
                            builder.environment()["OPENGMK_CONTROLS"] =
                                java.io.File(root, "controls.conf").absolutePath
                            val engineProcess = builder.start()
                            if (isWindows()) {
                                val selfExe = System.getProperty("jpackage.app-path")
                                val watcher = brandEngineWindow(engineProcess.pid(), root, log, selfExe, ::writeLog)
                                when {
                                    !watcher -> writeLog("No se pudo iniciar el vigilante; el launcher seguirá abierto.\n")
                                    selfExe.isNullOrBlank() -> writeLog(
                                        "Launcher sin jpackage.app-path; se mantiene abierto para poder volver.\n",
                                    )
                                    else -> onLauncherClose?.invoke()
                                }
                            } else {
                                val selfExe = System.getProperty("jpackage.app-path")
                                val watcher = brandLinuxEngineWindow(engineProcess.pid(), root, log, selfExe, ::writeLog)
                                when {
                                    !watcher -> writeLog("No se pudo iniciar el vigilante; el launcher seguirá abierto.\n")
                                    selfExe.isNullOrBlank() -> writeLog(
                                        "Launcher sin ruta de ejecutable; se mantiene abierto para poder volver.\n",
                                    )
                                    else -> onLauncherClose?.invoke()
                                }
                            }
                        }.onFailure { writeLog("No se pudo iniciar el motor: ${it.message}\n") }
                    }
                }
            },
            refreshRate = runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.displayMode.refreshRate.toFloat()
            }.getOrDefault(60f),
            appVersion = "0.6.7",
            fontFamily = fontFamily,
            colorScheme = colorScheme,
            shapes = shapes,
            logo = logo,
            onlineEngine = onlineEngine,
            desktopUi = true,
        )
    }

    application {
        val appIcon = painterResource("icon.png")
        Window(
            onCloseRequest = ::exitApplication,
            title = "Reloaded OpenGMK",
            icon = appIcon,
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp), placement = WindowPlacement.Maximized),
        ) {
            if (isWindows()) {
                val fontResolver = remember { createFontFamilyResolver() }
                CompositionLocalProvider(LocalFontFamilyResolver provides fontResolver) {
                    FluentTheme {
                        AppContent(
                            colorScheme = FluentTheme.colors.toMaterialScheme(),
                            shapes = Shapes(
                                extraSmall = RoundedCornerShape(4.dp),
                                small = RoundedCornerShape(6.dp),
                                medium = RoundedCornerShape(8.dp),
                                large = RoundedCornerShape(12.dp),
                                extraLarge = RoundedCornerShape(16.dp),
                            ),
                            logo = appIcon,
                            onLauncherClose = { exitApplication() },
                        )
                    }
                }
            } else {
                AppContent(logo = appIcon, onLauncherClose = { exitApplication() })
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
private fun Colors.toMaterialScheme(): ColorScheme {
    val base = if (darkMode) darkColorScheme() else lightColorScheme()
    val accent = fillAccent.default
    val onAccent = text.onAccent.primary
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.16f),
        onPrimaryContainer = text.text.primary,
        inversePrimary = accent,
        secondary = background.card.default,
        onSecondary = text.text.primary,
        secondaryContainer = background.card.default,
        onSecondaryContainer = text.text.primary,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = 0.16f),
        onTertiaryContainer = text.text.primary,
        background = background.solid.base,
        onBackground = text.text.primary,
        surface = background.layer.default,
        onSurface = text.text.primary,
        surfaceVariant = background.card.default,
        onSurfaceVariant = text.text.secondary,
        surfaceTint = accent,
        inverseSurface = text.text.primary,
        inverseOnSurface = background.solid.base,
        outline = stroke.card.default,
        outlineVariant = stroke.divider.default,
        scrim = Color.Black,
        error = system.critical,
        onError = onAccent,
        errorContainer = system.criticalBackground,
        onErrorContainer = text.text.primary,
        surfaceContainerLowest = background.solid.base,
        surfaceContainerLow = background.layer.default,
        surfaceContainer = background.card.default,
        surfaceContainerHigh = background.card.default,
        surfaceContainerHighest = background.card.default,
    )
}

private fun brandEngineWindow(
    enginePid: Long,
    root: File,
    logFile: File,
    appExe: String?,
    log: (String) -> Unit,
): Boolean = runCatching {
    val loader = Thread.currentThread().contextClassLoader
    val script = File(root, "win-retitle.ps1")
    val icon = File(root, "icon.png")
    loader.getResourceAsStream("win-retitle.ps1")?.use { script.writeBytes(it.readBytes()) }
    loader.getResourceAsStream("icon.png")?.use { icon.writeBytes(it.readBytes()) }
    if (!script.isFile) return@runCatching false
    val command = mutableListOf(
        "powershell.exe",
        "-NoProfile",
        "-WindowStyle",
        "Hidden",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.absolutePath,
        "-EnginePid",
        enginePid.toString(),
        "-IconPath",
        icon.absolutePath,
        "-LogPath",
        logFile.absolutePath,
    )
    if (!appExe.isNullOrBlank()) {
        command += "-AppExe"
        command += appExe
    }
    ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(File(root, "win-retitle.log")))
        .start()
    true
}.onFailure { log("No se pudo aplicar el título/icono al motor: ${it.message}\n") }
    .getOrDefault(false)

private fun brandLinuxEngineWindow(
    enginePid: Long,
    root: File,
    logFile: File,
    appExe: String?,
    log: (String) -> Unit,
): Boolean = runCatching {
    val loader = Thread.currentThread().contextClassLoader
    val script = File(root, "linux-engine-watch.sh")
    loader.getResourceAsStream("linux-engine-watch.sh")?.use { script.writeBytes(it.readBytes()) }
    if (!script.isFile) return@runCatching false
    script.setExecutable(true)
    val icon = writeLinuxIconData(root, log)?.absolutePath.orEmpty()
    ProcessBuilder(
        "setsid",
        "sh",
        script.absolutePath,
        enginePid.toString(),
        icon,
        logFile.absolutePath,
        appExe.orEmpty(),
        "Reloaded OpenGMK",
    ).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(File(root, "linux-engine-watch.log")))
        .start()
    true
}.onFailure { log("No se pudo iniciar el vigilante del motor: ${it.message}\n") }
    .getOrDefault(false)

private fun writeLinuxIconData(root: File, log: (String) -> Unit): File? = runCatching {
    val loader = Thread.currentThread().contextClassLoader
    val source = loader.getResourceAsStream("icon.png")?.use { ImageIO.read(it) }
        ?: return@runCatching null
    val size = 48
    val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.drawImage(source, 0, 0, size, size, null)
    graphics.dispose()
    val data = StringBuilder("$size,$size")
    for (y in 0 until size) {
        for (x in 0 until size) {
            data.append(',').append(scaled.getRGB(x, y).toLong() and 0xFFFFFFFFL)
        }
    }
    File(root, "icon.xprop").apply { writeText(data.toString()) }
}.onFailure { log("No se pudo preparar el icono para Linux: ${it.message}\n") }
    .getOrNull()
