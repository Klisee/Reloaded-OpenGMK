package com.klisee.reloaded.gmk

import java.io.File

fun main(args: Array<String>) {
    val home = System.getProperty("user.home")
    val root = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        File(System.getenv("APPDATA") ?: home, "Reloaded OpenGMK")
    } else {
        File(home, ".local/share/Reloaded OpenGMK")
    }
    if (args.firstOrNull() == "--import" && args.size >= 2) {
        val result = kotlinx.coroutines.runBlocking {
            GameRepository(root).importZip(File(args[1]), onLog = { println(it) })
        }
        println(result.fold({ "IMPORT OK" }, { "IMPORT FAIL: ${it.stackTraceToString()}" }))
        return
    }
    runWindowsApplication(root)
}

internal fun findEngine(root: File): File? {
    System.getenv("OPENGMK_ENGINE")?.let { path ->
        val file = File(path)
        if (file.isFile) return file
    }
    val engineName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "gm8emulator.exe"
    } else {
        "gm8emulator"
    }
    val installed = File(root, "engine/$engineName")
    // El SoundFont de MIDI viaja dentro del motor, así que no hace falta copiarlo. Pero si hay uno
    // en la carpeta del motor, el motor lo prefiere: así se puede cambiar la música MIDI sin
    // recompilar. Esta función solo garantiza que el motor esté al día.
    val bundled = System.getProperty("compose.application.resources.dir")
        ?.let { File(it, engineName) }
        ?.takeIf { it.isFile }
    if (bundled != null) {
        installed.parentFile?.mkdirs()
        if (!installed.isFile || installed.length() != bundled.length()) {
            bundled.copyTo(installed, overwrite = true)
        }
        installed.setExecutable(true)
        return installed
    }
    val classpathEngine = Thread.currentThread().contextClassLoader?.getResourceAsStream(engineName)
        ?: ClassLoader.getSystemResourceAsStream(engineName)
    if (classpathEngine != null) {
        installed.parentFile?.mkdirs()
        classpathEngine.use { input ->
            installed.outputStream().use { output -> input.copyTo(output) }
        }
        installed.setExecutable(true)
        return installed
    }
    return listOf(
        File(root, "engine/$engineName"),
        File("/usr/local/bin/$engineName"),
        File(System.getProperty("user.dir"), engineName),
    ).firstOrNull { it.isFile && it.canExecute() }
}
