package com.klisee.reloaded.gmk.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Canales de actualización que puede seguir el launcher. */
const val UPDATE_CHANNEL_STABLE = "stable"
const val UPDATE_CHANNEL_BETA = "beta"

/** Cómo se llama el asset de cada plataforma. Tiene que coincidir con el workflow de release. */
enum class UpdatePlatform(val fileName: String) {
    Windows("reloaded-opengmk-{version}-win-x64.msi"),
    LinuxDeb("reloaded-opengmk_{version}_amd64.deb"),
    LinuxAppImage("reloaded-opengmk-{version}-linux-x86_64.AppImage"),
    AndroidArm64("reloaded-opengmk-{version}-android-arm64.apk"),
    ;

    /** El nombre ideal, que es el que publica el workflow de release. */
    fun assetName(version: String): String = fileName.replace("{version}", version)

    /** Extensión que identifica el paquete de esta plataforma. */
    val extension: String get() = when (this) {
        Windows -> ".msi"
        LinuxDeb -> ".deb"
        LinuxAppImage -> ".appimage"
        AndroidArm64 -> ".apk"
    }

    /** Un archivo vale si termina en la extensión correcta, sin importar cómo lo haya nombrado el empaquetador. */
    fun matches(name: String): Boolean = name.endsWith(extension, ignoreCase = true)

    companion object {
        /** La plataforma en la que corre este binario, o `null` si no hay actualizador para ella. */
        fun current(): UpdatePlatform? = platformUpdateTarget()
    }
}

/** Dónde está corriendo la app; lo resuelve cada plataforma. */
expect fun platformUpdateTarget(): UpdatePlatform?

/** Lo que devuelve `releases?per_page=50` de GitHub, recortado a lo que usamos. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
) {
    val version: String get() = tagName.removePrefix("v").removePrefix("V")
    val parsedVersion: AppVersion? get() = AppVersion.parse(version)
    val isStable: Boolean get() = !draft && !prerelease && parsedVersion?.isPrerelease == false

    /**
     * El paquete de [platform] para esta release.
     *
     * Se prefiere el nombre exacto que publica el workflow, pero se acepta cualquier archivo con la
     * extensión correcta: el empaquetador (jpackage/Nucleus) decide el nombre final, y un `.deb`
     * llamado `algo-0.5.0-linux-amd64.deb` sigue siendo el `.deb` de esta plataforma. Atarse al
     * nombre exacto hacía que una release real no se ofreciera nunca.
     */
    fun assetFor(platform: UpdatePlatform): GitHubAsset? {
        val uploaded = assets.filter { it.state == "uploaded" && platform.matches(it.name) }
        if (uploaded.isEmpty()) return null
        val exact = platform.assetName(version)
        return uploaded.firstOrNull { it.name.equals(exact, ignoreCase = true) }
            ?: uploaded.firstOrNull { it.name.contains(version, ignoreCase = true) }
            ?: uploaded.first()
    }
}

@Serializable
data class GitHubAsset(
    val name: String,
    val state: String = "uploaded",
    val size: Long = 0,
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String,
) {
    /** SHA-256 que publica GitHub como `sha256:<hex>`, ya en bytes listos para comparar. */
    val sha256: ByteArray?
        get() {
            val hex = digest?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?: return null
            if (hex.length != 64 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return ByteArray(32) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }
}

/**
 * Elige la actualización que corresponde ofrecer.
 *
 * - En canal estable solo cuentan las versiones publicadas sin etiqueta de preestreno.
 * - En canal beta también las etiquetas `-beta`, pero nunca un borrador.
 * - Solo se ofrece algo si el asset de esta plataforma existe y ya terminó de subirse.
 */
fun pickUpdate(
    releases: List<GitHubRelease>,
    installedVersion: String,
    platform: UpdatePlatform,
    channel: String = UPDATE_CHANNEL_STABLE,
): GitHubRelease? {
    val installed = AppVersion.parse(installedVersion) ?: return null
    val allowPrerelease = channel == UPDATE_CHANNEL_BETA
    return releases
        .asSequence()
        .filter { !it.draft }
        .filter { allowPrerelease || it.isStable }
        .filter { release -> release.parsedVersion?.let { it > installed } == true }
        .sortedByDescending { it.parsedVersion }
        // Sin el paquete de ESTA plataforma no hay nada que ofrecer: bajar el de otra no serviría.
        .firstOrNull { release -> release.assetFor(platform) != null }
}

/** Igual que [pickUpdate], pero para pintar el historial de cambios sin filtrar por versión. */
fun releaseHistory(
    releases: List<GitHubRelease>,
    channel: String = UPDATE_CHANNEL_STABLE,
): List<GitHubRelease> {
    val allowPrerelease = channel == UPDATE_CHANNEL_BETA
    return releases
        .filter { !it.draft && (allowPrerelease || it.isStable) && it.parsedVersion != null }
        .sortedByDescending { it.parsedVersion }
}

/** Repositorio del que se leen las releases. */
const val UPDATE_REPOSITORY = "Klisee/Reloaded-OpenGMK"

/** Página de releases, para el botón «ver en GitHub». */
const val UPDATE_RELEASES_PAGE = "https://github.com/$UPDATE_REPOSITORY/releases"
