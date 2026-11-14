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
