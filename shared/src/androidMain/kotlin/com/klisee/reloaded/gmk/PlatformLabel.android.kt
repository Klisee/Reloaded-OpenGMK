package com.klisee.reloaded.gmk

import android.os.Build

actual fun platformLabel(): String {
    val abis = Build.SUPPORTED_ABIS
    val abi = when {
        abis.any { it.contains("arm64") } -> "arm64"
        abis.any { it.contains("armeabi") } -> "armv7"
        abis.any { it.contains("x86_64") } -> "x86_64"
        abis.any { it == "x86" } -> "x86"
        else -> abis.firstOrNull() ?: "arm64"
    }
    val version = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }
    return if (version != null) "Android $version · $abi" else "Android · $abi"
}

actual fun gpuBackendLabel(): String = "Vulkan"

/**
 * En Android el tema lo resuelve `isSystemInDarkTheme()` de Compose, que sí funciona; se devuelve
 * `null` para que el llamador use esa vía en vez de duplicar la lógica.
 */
actual fun systemPrefersDark(): Boolean? = null
