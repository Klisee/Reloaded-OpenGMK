package com.klisee.reloaded.gmk

actual fun platformLabel(): String {
    val os = System.getProperty("os.name").orEmpty()
    val arch = System.getProperty("os.arch").orEmpty()
    val name = when {
        os.startsWith("Windows", ignoreCase = true) -> "Windows"
        os.startsWith("Mac", ignoreCase = true) || os.startsWith("Darwin", ignoreCase = true) -> "macOS"
        os.startsWith("Linux", ignoreCase = true) -> "Linux"
        else -> os.ifBlank { "Desktop" }
    }
    val abi = when (arch.lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> arch
    }
    return if (abi.isBlank()) name else "$name $abi"
}

actual fun gpuBackendLabel(): String {
    val os = System.getProperty("os.name").orEmpty()
    return if (os.startsWith("Windows", ignoreCase = true)) "DirectX 12" else "Vulkan"
}

/**
 * Lee el tema oscuro del sistema de forma nativa.
 *
 * En Windows, `AppsUseLightTheme` es el valor que usa el Escritorio para decidir si las
 * aplicaciones se dibujan en claro u oscuro (0 = oscuro, 1 = claro). Compose Desktop no lo lee, así
 * que `isSystemInDarkTheme()` devuelve siempre `false` y la app se quedaba en claro.
 */
actual fun systemPrefersDark(): Boolean? {
    val os = System.getProperty("os.name").orEmpty()
    if (!os.startsWith("Windows", ignoreCase = true)) return null
    return runCatching {
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
}
