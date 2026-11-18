package com.klisee.reloaded.gmk

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.Window
import java.io.File

/**
 * Aplica el tema oscuro de Windows a la ventana del launcher.
 *
 * Compose Desktop dibuja su interfaz, pero la barra de título y el borde los pinta Windows: sin
 * avisar, quedan en blanco aunque la app sea oscura.
 *
 * El HWND se localiza por PID con `EnumWindows`. Antes se intentaba por reflexión sobre
 * `getPeer()` de AWT, que es protegido y devuelve null: el handle nunca se obtenía y la barra se
 * quedaba blanca. Se usa `jna-platform`, que ya viene con Compose Desktop.
 */
internal object WindowsDarkTitleBar {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_BORDER_COLOR = 34

    private val dwmapi by lazy { Native.load("dwmapi", DwmApi::class.java) }

    private interface DwmApi : com.sun.jna.Library {
        fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int
    }

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
     * arrancaba en claro. `AppsUseLightTheme` decide si las aplicaciones se dibujan en claro u
     * oscuro. Devuelve `null` si no se puede leer.
     */
    fun systemPrefersDark(): Boolean? = runCatching {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val match = Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x([0-9a-fA-F]+)""").find(text)
            ?: return null
        match.groupValues[1].toInt(16) == 0
    }.getOrNull()

    private fun buildNumber(): Int = runCatching {
        val process = ProcessBuilder("cmd", "/c", "ver").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("""\d+\.\d+\.(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }.getOrDefault(0)

    /**
     * HWND visible de nivel superior del proceso actual.
     *
     * `EnumWindows` recorre las ventanas del escritorio y `GetWindowThreadProcessId` dice a qué
     * proceso pertenece cada una. Se toma la primera visible del propio proceso.
     */
    private fun windowHandle(): Long? = runCatching {
        val pid = ProcessHandle.current().pid().toInt()
        var found: Long = 0

        val callback = object : WinUser.WNDENUMPROC {
            override fun callback(hwnd: HWND?, data: Pointer?): Boolean {
                if (found != 0L || hwnd == null) return true
                val owner = IntByReference()
                User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner)
                if (owner.value == pid && User32.INSTANCE.IsWindowVisible(hwnd)) {
                    found = Pointer.nativeValue(hwnd.pointer)
                }
                return true
            }
        }
        User32.INSTANCE.EnumWindows(callback, null)
        if (found == 0L) null else found
    }.getOrNull()

    /** Aplica el modo oscuro a la barra de título. Devuelve si DWM aceptó el atributo. */
    fun applyAndLog(window: Window, dark: Boolean): Boolean {
        if (!isWindows()) return false
        val handle = windowHandle() ?: return false
        val hwnd = HWND(Pointer(handle))
        val value = IntByReference(if (dark) 1 else 0)

        val ok = dwmapi.DwmSetWindowAttribute(
            hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value.pointer, 4,
        ) == 0 || dwmapi.DwmSetWindowAttribute(
            hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, value.pointer, 4,
        ) == 0

        if (ok && isWindows11 && dark) {
            val caption = IntByReference(0x00202020)
            dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, caption.pointer, 4)
            val border = IntByReference(0x003D3D3D)
            dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, border.pointer, 4)
        }
        return ok
    }

    fun logTo(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(text)
        }
    }
}
