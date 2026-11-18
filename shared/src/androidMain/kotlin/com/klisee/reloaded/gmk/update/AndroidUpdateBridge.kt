package com.klisee.reloaded.gmk.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

actual fun platformUpdateTarget(): UpdatePlatform? =
    if (Build.SUPPORTED_ABIS.any { it.contains("arm64") }) UpdatePlatform.AndroidArm64 else null

/**
 * Android: el paquete descargado se abre con el instalador del sistema.
 *
 * Se usa un `FileProvider` porque desde Android 7 el instalador no puede leer archivos del almacenamiento
 * privado de la app por ruta (`file://`). El `content://` le da permiso temporal sobre ese único archivo,
 * y el sistema se encarga de pedirle al usuario la confirmación de instalar.
 */
class AndroidUpdateBridge(
    private val context: Context,
    private val version: String,
) : JvmUpdateBridge(File(context.filesDir, "updates-cache")) {
    override val platform: UpdatePlatform = UpdatePlatform.AndroidArm64
    override val installedVersion: String = version

    override fun userAgent(): String = "Reloaded-OpenGMK/$version"

    /**
     * Android 8+ exige que el usuario autorice a esta app instalar paquetes. Si aún no lo hizo, se
     * lleva a esa pantalla concreta en vez de fallar en silencio: es un paso que solo se da una vez.
     */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || canInstallPackages()) return
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override suspend fun install(path: String): Boolean = runCatching {
        if (!canInstallPackages()) {
            requestInstallPermission()
            return@runCatching false
        }
        val file = File(path)
        if (!file.isFile) return@runCatching false
        // Antes de abrir el instalador se comprueba que el paquete sea el nuestro y esté firmado con
        // la misma clave que el instalado. Sin esto, un APK ajeno con el mismo nombre de paquete
        // podría intentar reemplazar la app (Android lo rechazaría, pero es mejor no ofrecerlo).
        if (!verifyPackageIdentity(file)) return@runCatching false
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * `true` solo si [file] declara nuestro paquete y comparte la firma con la app instalada.
     * Se comparan los hashes de los certificados, que es lo que hace Android para permitir una
     * actualización sobre otra versión.
     */
    private fun verifyPackageIdentity(file: File): Boolean = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedKeys = signerHashes(installed)
        val archiveKeys = signerHashes(archive)
        if (installedKeys.isEmpty() || archiveKeys.isEmpty()) return false
        // Basta con que coincida una: Android permite rotar la clave y el APK nuevo puede venir
        // firmado con la nueva mientras el instalado conserva la anterior en el historial.
        archiveKeys.any { it in installedKeys } ||
            signerHashes(archive, history = true).any { it in installedKeys }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun signerHashes(info: android.content.pm.PackageInfo, history: Boolean = false): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (history) signing.signingCertificateHistory else signing.apkContentsSigners
        } else {
            info.signatures
        }.orEmpty()
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * La regla de firmas, separada de Android para poder probarla: `true` si el APK descargado puede
 * actualizar al instalado.
 *
 * - Se comparan hashes SHA-256 de los certificados, no los certificados en sí.
 * - Un APK sin firma nunca es válido.
 * - El historial del instalado cubre la rotación de clave: Android acepta un APK firmado con la
 *   clave nueva si la vieja aparece en el historial.
 */
internal fun signersMatch(
    installedCurrent: Set<String>,
    installedHistory: Set<String>,
    archiveCurrent: Set<String>,
    archiveHistory: Set<String>,
): Boolean {
    if (installedCurrent.isEmpty() || archiveCurrent.isEmpty()) return false
    val trusted = installedCurrent + installedHistory
    return archiveCurrent.any { it in trusted } || archiveHistory.any { it in installedCurrent }
}
