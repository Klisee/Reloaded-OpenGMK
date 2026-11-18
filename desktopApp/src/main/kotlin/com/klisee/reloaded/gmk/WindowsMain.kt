package com.klisee.reloaded.gmk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

/**
 * WM_CLASS del launcher en Linux. Viene de `startupWMClass` en `build.gradle.kts`; el motor usa
 * el mismo para que el dock agrupe ambas ventanas como una sola aplicación, con su icono y nombre.
 */
internal const val LAUNCHER_WM_CLASS = "com-klisee-reloaded-gmk-MainKt"

private fun isWindows(): Boolean =
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

@OptIn(ExperimentalFluentApi::class)
fun runWindowsApplication(root: File) {
    val appVersion = "0.6.7"
    val updateManager = com.klisee.reloaded.gmk.update.UpdateManager(
        bridge = com.klisee.reloaded.gmk.update.DesktopUpdateBridge(root, appVersion),
        settings = com.klisee.reloaded.gmk.update.FileUpdateSettings(root),
        userAgent = "Reloaded-OpenGMK/$appVersion",
    )
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
        onEngineStarted: ((Process) -> Unit)? = null,
        onDarkModeChanged: ((String) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        App(
            repo = repo,
            settings = settings,
            onDarkModeChanged = onDarkModeChanged,
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
                        // El motor entiende dos backends: `gl` (OpenGL histórico) y `wgpu`. Para
                        // wgpu, `OPENGMK_WGPU_BACKEND` fija la API concreta; sin él, wgpu elige la
                        // mejor disponible por rendimiento.
                        val backend = when (settings.renderer) {
                            "opengl" -> "gl"
                            "wgpu", "dx12", "vulkan", "gl" -> "wgpu"
                            else -> null
                        }
                        val wgpuApi = when (settings.renderer) {
                            WGPU_BACKEND_DX12, WGPU_BACKEND_VULKAN, WGPU_BACKEND_GL -> settings.renderer
                            else -> null
                        }
                        writeLog(
                            "Video: render=$renderMode showFps=${settings.showFps} " +
                                "backend=${backend ?: "auto"} wgpuApi=${wgpuApi ?: "auto"}\n",
                        )
                        runCatching {
                            val readyMarker = File(root, "engine-ready")
                            readyMarker.delete()
                            val builder = ProcessBuilder(engine.absolutePath, exe.absolutePath)
                                .directory(jvmRepo.gameDir)
                                .redirectErrorStream(true)
                                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                            builder.environment()["OPENGMK_RENDER"] = renderMode
                            if (backend != null) builder.environment()["OPENGMK_RENDERER"] = backend
                            if (wgpuApi != null) builder.environment()["OPENGMK_WGPU_BACKEND"] = wgpuApi
                            builder.environment()["OPENGMK_SHOW_FPS"] = if (settings.showFps) "1" else "0"
                            builder.environment()["OPENGMK_SCALING"] = if (settings.keepAspect) "aspect" else "full"
                            builder.environment()["OPENGMK_RENDER_HZ"] = detectedHz.toString()
                            builder.environment()["OPENGMK_CONTROLS"] =
                                java.io.File(root, "controls.conf").absolutePath
                            builder.environment()["OPENGMK_READY_FILE"] = readyMarker.absolutePath
                            // Que la ventana del juego se agrupe con el launcher: mismo WM_CLASS,
                            // mismo icono y mismo nombre en el dock, en vez de "OpenGMK" aparte.
                            // RESOURCE_NAME fija la instancia; el motor pone la clase.
                            builder.environment()["OPENGMK_WM_CLASS"] = LAUNCHER_WM_CLASS
                            builder.environment()["RESOURCE_NAME"] = LAUNCHER_WM_CLASS
                            val engineProcess = builder.start()
                            onEngineStarted?.invoke(engineProcess)
                            val selfExe = System.getProperty("jpackage.app-path")
                            val watcher = if (isWindows()) {
                                brandEngineWindow(engineProcess.pid(), root, log, selfExe, ::writeLog)
                            } else {
                                brandLinuxEngineWindow(engineProcess.pid(), root, log, selfExe, ::writeLog)
                            }
                            if (!watcher) {
                                writeLog("No se pudo iniciar el vigilante; el launcher seguirá abierto.\n")
                            }
                        }.onFailure {
                            writeLog("No se pudo iniciar el motor: ${it.message}\n")
                        }
                    }
                }
            },
            refreshRate = runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.displayMode.refreshRate.toFloat()
            }.getOrDefault(60f),
            appVersion = appVersion,
            updateManager = updateManager,
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
        // Mientras el motor arranca, el launcher se queda con la pantalla "Cargando..." (igual
        // que en Android). Cuando el motor ya pintó su primer frame, la ventana del juego se
        // queda delante y el launcher pasa detrás (sin minimizar ni ocultar: en Wayland/XWayland
        // ni ocultar ni minimizar la última ventana son fiables, y ocultarla puede terminar la
        // app). Al salir del juego el launcher vuelve al frente, sin cerrarlo ni relanzarlo.
        var launching by remember { mutableStateOf(false) }
        var engine by remember { mutableStateOf<Process?>(null) }
        var engineReady by remember { mutableStateOf(false) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Reloaded OpenGMK",
            icon = appIcon,
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp), placement = WindowPlacement.Maximized),
        ) {
            LaunchedEffect(engine) {
                val process = engine
                if (process == null) {
                    if (!launching) {
                        engineReady = false
                    }
                    return@LaunchedEffect
                }
                val marker = File(root, "engine-ready")
                val started = System.currentTimeMillis()
                try {
                    while (process.isAlive) {
                        if (!engineReady) {
                            val signalled = marker.isFile
                            val timedOut = System.currentTimeMillis() - started > 25_000
                            if (signalled || timedOut) {
                                marker.delete()
                                engineReady = true
                                // El motor ya tiene píxeles en pantalla: nos apartamos de su camino.
                                // En Windows `toBack` no aparta una ventana maximizada (el WM la
                                // vuelve a traer al frente), así que ahí se minimiza; en Wayland
                                // minimizar es lo que no es fiable, y `toBack` sí funciona.
                                runCatching {
                                    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                                        window.extendedState = window.extendedState or java.awt.Frame.ICONIFIED
                                    } else {
                                        window.toBack()
                                    }
                                }
                            }
                        }
                        kotlinx.coroutines.delay(120)
                    }
                } finally {
                    marker.delete()
                    engineReady = false
                    launching = false
                    runCatching {
                        if (window.extendedState and java.awt.Frame.ICONIFIED != 0) {
                            window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
                        }
                        window.toFront()
                        window.requestFocus()
                    }
                }
            }
            if (isWindows()) {
                val fontResolver = remember { createFontFamilyResolver() }
                // El modo claro/oscuro es un ajuste de la app. Antes se pasaba
                // `FluentTheme.colors.toMaterialScheme()` como override, y eso anulaba toda la
                // paleta: `FluentTheme` detecta el tema con la misma función de Compose Desktop
                // que no lee el tema de Windows, así que devolvía siempre colores claros y la
                // interfaz se quedaba en blanco aunque el ajuste dijera oscuro. Ahora la paleta
                // la construye `ReloadedTheme` a partir de `darkMode` (PlatformTheme.desktop.kt) y
                // Fluent solo aporta sus CompositionLocals para la selección de texto.
                var darkModeState by remember { mutableStateOf(settings.darkMode) }
                val themeSource = if (darkModeState == "auto") {
                    if (WindowsDarkTitleBar.systemPrefersDark() == true) "sistema (registro)" else "sistema (claro)"
                } else {
                    "ajuste"
                }
                val dark = when (darkModeState) {
                    "dark" -> true
                    "light" -> false
                    else -> WindowsDarkTitleBar.systemPrefersDark() ?: isSystemInDarkTheme()
                }
                LaunchedEffect(dark) {
                    val logFile = File(root, "app.log")
                    WindowsDarkTitleBar.logTo(
                        logFile,
                        "Tema de ventana: dark=$dark ($themeSource) windows11=${WindowsDarkTitleBar.isWindows11}\n",
                    )
                    // La ventana tarda en existir: se reintenta hasta ~3 s. Antes se intentaba una
                    // sola vez con un HWND que nunca se obtenía, y la barra quedaba blanca.
                    repeat(30) { attempt ->
                        if (WindowsDarkTitleBar.applyAndLog(window, dark)) {
                            WindowsDarkTitleBar.logTo(logFile, "Barra de titulo: aplicada en el intento ${attempt + 1}\n")
                            return@LaunchedEffect
                        }
                        kotlinx.coroutines.delay(100)
                    }
                    WindowsDarkTitleBar.logTo(logFile, "Barra de titulo: no se pudo aplicar\n")
                }
                CompositionLocalProvider(LocalFontFamilyResolver provides fontResolver) {
                    FluentTheme {
                        DesktopOverlayRoot(visible = launching && !engineReady, logo = appIcon) {
                            AppContent(
                                shapes = Shapes(
                                    extraSmall = RoundedCornerShape(4.dp),
                                    small = RoundedCornerShape(6.dp),
                                    medium = RoundedCornerShape(8.dp),
                                    large = RoundedCornerShape(12.dp),
                                    extraLarge = RoundedCornerShape(16.dp),
                                ),
                                logo = appIcon,
                                onDarkModeChanged = { darkModeState = it },
                                onLauncherClose = { exitApplication() },
                                onEngineStarted = {
                                    launching = true
                                    engine = it
                                },
                            )
                        }
                    }
                }
            } else {
                DesktopOverlayRoot(visible = launching && !engineReady, logo = appIcon) {
                    AppContent(
                        logo = appIcon,
                        onLauncherClose = { exitApplication() },
                        onEngineStarted = {
                            launching = true
                            engine = it
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
private fun Colors.toMaterialScheme(dark: Boolean): ColorScheme {
    // Sin uso desde que la paleta la construye ReloadedTheme. Se conserva por si se quiere volver a
    // derivar del tema de Fluent, pero ojo: Fluent detecta el tema con isSystemInDarkTheme(), que en
    // escritorio devuelve siempre claro, así que sus colores no sirven para el modo oscuro.
    val base = if (dark) darkColorScheme() else lightColorScheme()
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
