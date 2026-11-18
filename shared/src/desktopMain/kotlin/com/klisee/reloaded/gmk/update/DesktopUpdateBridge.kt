package com.klisee.reloaded.gmk.update

import java.io.File

actual fun platformUpdateTarget(): UpdatePlatform? {
    val os = System.getProperty("os.name").orEmpty()
    val arch = System.getProperty("os.arch").orEmpty()
    val isArm = arch.equals("aarch64", ignoreCase = true) || arch.equals("arm64", ignoreCase = true)
    return when {
        os.startsWith("Windows", ignoreCase = true) -> UpdatePlatform.Windows
        os.startsWith("Linux", ignoreCase = true) && !isArm -> {
            // Si la app se empaquetó como AppImage, el usuario ya está usando ese formato y
            // conviene ofrecerle el mismo; en cualquier otro caso, el .deb es lo instalable.
            if (isRunningAppImage()) UpdatePlatform.LinuxAppImage else UpdatePlatform.LinuxDeb
        }
        // macOS y Linux arm64 no tienen paquete todavía: mejor no ofrecer nada que romper la app.
        else -> null
    }
}

/** AppImage define `APPIMAGE` con la ruta del archivo que se está ejecutando. */
private fun isRunningAppImage(): Boolean = !System.getenv("APPIMAGE").isNullOrBlank()

/**
 * Escritorio: descarga el paquete y lo abre con el instalador del sistema.
 *
 * No reemplaza archivos por su cuenta: un `.deb` o un `.msi` necesitan al gestor de paquetes o al
 * instalador de Windows, que ya saben actualizar, registrar y pedir permisos correctamente. La
 * verificación SHA-256 de la descarga es lo que hace seguro el paso anterior.
 */
class DesktopUpdateBridge(root: File, private val version: String) : JvmUpdateBridge(root) {
    override val platform: UpdatePlatform = platformUpdateTarget() ?: UpdatePlatform.LinuxDeb
    override val installedVersion: String = version

    override fun userAgent(): String = "Reloaded-OpenGMK/$version"

    override suspend fun install(path: String): Boolean = runCatching {
        val file = File(path)
        if (!file.isFile) return@runCatching false
        val os = System.getProperty("os.name").orEmpty()
        when {
            // `start` es un builtin de cmd; con la ruta entre comillas abre el instalador asociado.
            os.startsWith("Windows", ignoreCase = true) ->
                ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
            // En Linux se le pide al escritorio que abra el paquete con su instalador gráfico.
            else -> ProcessBuilder("xdg-open", file.absolutePath).start()
        }
        true
    }.getOrDefault(false)
}
