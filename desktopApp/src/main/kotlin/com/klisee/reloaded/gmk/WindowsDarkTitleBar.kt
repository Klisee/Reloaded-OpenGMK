package com.klisee.reloaded.gmk

import java.awt.Window
import java.io.File

/**
 * Tema de Windows para la ventana del launcher.
 *
 * Compose Desktop dibuja su interfaz, pero la barra de título y el borde los pinta Windows: sin
 * avisar, quedan en blanco aunque la app sea oscura.
 *
 * No se añaden dependencias. Se usa reflexión sobre `com.sun.jna` **solo si está en el runtime**
 * (viene de forma transitiva con Compose Desktop en el paquete final, pero no está en el
 * classpath de compilación). Si no está, la app funciona igual: solo no se pinta la barra.
 */
internal object WindowsDarkTitleBar {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_BORDER_COLOR = 34

    fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    val isWindows11: Boolean by lazy {
        if (!isWindows()) return@lazy false
        buildNumber() >= 22000
    }

    /**
     * Lee el tema de Windows del registro.
     *
     * `isSystemInDarkTheme()` de Compose Desktop no lee el tema de Windows, así que la app siempre
     * arrancaba en claro. `AppsUseLightTheme` es el valor que usa Windows 11 para decidir si las
     * aplicaciones se dibujan en claro u oscuro. Devuelve `null` si no se puede leer.
     */
    fun systemPrefersDark(): Boolean? = runCatching {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val match = Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x([0-9a-fA-F]+)""").find(text) ?: return null
        match.groupValues[1].toInt(16) == 0
    }.getOrNull()

    private fun buildNumber(): Int = runCatching {
        val process = ProcessBuilder("cmd", "/c", "ver").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("""\d+\.\d+\.(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }.getOrDefault(0)

    /**
     * Pinta la barra de título en oscuro. Debe llamarse cuando la ventana ya es *displayable*
     * (después de que Compose la muestre).
     */
    fun apply(window: Window, dark: Boolean) {
        if (!isWindows()) return
        runCatching {
            val hwnd = hwndOf(window) ?: return
            setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) ||
                setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, if (dark) 1 else 0)
            if (isWindows11 && dark) {
                setIntAttribute(hwnd, DWMWA_CAPTION_COLOR, 0x00202020)
                setIntAttribute(hwnd, DWMWA_BORDER_COLOR, 0x003D3D3D)
            }
        }
    }

    private fun hwndOf(window: Window): Long? = runCatching {
        val peer = window.javaClass.getMethod("getPeer").invoke(window) ?: return null
        val hwnd = peer.javaClass.getMethod("getHWnd").invoke(peer) as Long
        if (hwnd == 0L) null else hwnd
    }.getOrNull()

    /**
     * Llama a `DwmSetWindowAttribute` por reflexión. Devuelve `true` si la llamada tuvo éxito.
     *
     * La firma nativa es `(HWND, DWORD, LPCVOID, DWORD)`. Con JNA se puede invocar sin declarar
     * nada más; el `int[]` del valor viaja como puntero.
     */
    private fun setIntAttribute(hwnd: Long, attribute: Int, value: Int): Boolean = runCatching {
        val native = Class.forName("com.sun.jna.Native")
        val nativeLibrary = Class.forName("com.sun.jna.NativeLibrary")
        val dwmapi = nativeLibrary.getMethod("getInstance", String::class.java).invoke(null, "dwmapi")
        val function = nativeLibrary.getMethod("getFunction", String::class.java)
            .invoke(dwmapi, "DwmSetWindowAttribute")
        val invokeInt = function!!.javaClass.getMethod(
            "invokeInt",
            Array<Any>::class.java,
        )
        val args = arrayOf<Any>(hwnd, attribute, intArrayOf(value), 4)
        invokeInt.invoke(function, args) as Int == 0
    }.getOrDefault(false)

    /// Escribe un diagnóstico en el log. Sirve para saber si el sistema aplicó el tema o no.
    fun logTo(file: File, dark: Boolean, source: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("Tema de ventana: dark=$dark ($source) windows11=$isWindows11\n")
        }
    }
}
