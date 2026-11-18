package com.klisee.reloaded.gmk.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Milisegundos del reloj del sistema; `System.currentTimeMillis` sirve en JVM y Android. */
actual fun nowMillis(): Long = System.currentTimeMillis()

/** Se guardan en un `.properties` propio para no tocar el formato de `settings.properties`. */
class FileUpdateSettings(private val root: File) : UpdateSettings {
    private val file = File(root, "updates.properties")
    private val values = java.util.Properties().apply {
        if (file.isFile) runCatching { file.inputStream().use { load(it) } }
    }

    override var autoCheck: Boolean
        get() = values.getProperty("autoCheck", "1") == "1"
        set(value) = put("autoCheck", if (value) "1" else "0")

    override var channel: String
        get() = values.getProperty("channel", UPDATE_CHANNEL_STABLE)
            .takeIf { it == UPDATE_CHANNEL_BETA } ?: UPDATE_CHANNEL_STABLE
        set(value) = put("channel", if (value == UPDATE_CHANNEL_BETA) UPDATE_CHANNEL_BETA else UPDATE_CHANNEL_STABLE)

    override var notifications: Boolean
        get() = values.getProperty("notifications", "1") == "1"
        set(value) = put("notifications", if (value) "1" else "0")

    override var lastCheckAt: Long
        get() = values.getProperty("lastCheckAt", "0").toLongOrNull() ?: 0L
        set(value) = put("lastCheckAt", value.toString())

    override var lastAttemptAt: Long
        get() = values.getProperty("lastAttemptAt", "0").toLongOrNull() ?: 0L
        set(value) = put("lastAttemptAt", value.toString())

    override var cachedReleasesJson: String
        get() = values.getProperty("cachedReleases", "")
        set(value) = put("cachedReleases", value)

    override var lastNotifiedKey: String
        get() = values.getProperty("lastNotifiedKey", "")
        set(value) = put("lastNotifiedKey", value)

    private fun put(key: String, value: String) {
        values.setProperty(key, value)
        root.mkdirs()
        runCatching { file.outputStream().use { values.store(it, "Reloaded OpenGMK - actualizaciones") } }
    }
}

/**
 * Base para escritorio y Android: lo único que cambia entre ellos es cómo se instala el archivo
 * descargado, así que eso queda como método abstracto.
 */
abstract class JvmUpdateBridge(private val root: File) : UpdatePlatformBridge {
    protected val json = Json { ignoreUnknownKeys = true }

    /** Carpeta donde se deja el instalador descargado. */
    protected val downloadDirectory: File get() = File(root, "updates")

    override suspend fun fetchReleases(): String = withContext(Dispatchers.IO) {
        githubGet("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=50")
    }

    override suspend fun download(asset: GitHubAsset, onProgress: (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            // Sin digest no se descarga: es la única forma de saber que llegó lo que GitHub publicó.
            val expected = asset.sha256
                ?: throw DigestMismatch("La release no publica el SHA-256 de ${asset.name}")
            require(asset.size in 1..MAX_DOWNLOAD_BYTES) { "Tamaño de descarga inválido: ${asset.size}" }

            // El nombre final lo decide el paquete publicado, no el que esperábamos: si el
            // empaquetador lo llamó de otra forma, se guarda tal cual para que el sistema sepa
            // con qué programa abrirlo.
            val directory = downloadDirectory.apply { mkdirs() }
            // Se limpian restos de intentos anteriores para no acumular instaladores viejos.
            directory.listFiles()?.forEach { it.delete() }
            val target = File(directory, asset.name)
            val partial = File(directory, "${asset.name}.part")
            partial.delete()

            try {
                val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent())
                    setRequestProperty("Accept", "application/octet-stream")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("La descarga respondió ${connection.responseCode}")
                }
                val declared = connection.contentLengthLong
                require(declared < 0 || declared == asset.size) { "El tamaño anunciado no coincide" }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                onProgress(0)
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            require(downloaded <= asset.size) { "La descarga superó el tamaño esperado" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            onProgress(((downloaded * 100) / asset.size).toInt().coerceIn(0, 100))
                        }
                    }
                }
                if (downloaded != asset.size) {
                    throw DigestMismatch("La descarga quedó incompleta ($downloaded de ${asset.size} bytes)")
                }
                if (!MessageDigest.isEqual(digest.digest(), expected)) {
                    throw DigestMismatch("El SHA-256 no coincide con el publicado")
                }
                // `renameTo` es atómico en el mismo sistema de archivos y evita dejar un archivo a medias.
                check(partial.renameTo(target)) { "No pude mover el instalador descargado" }
                onProgress(100)
                target.absolutePath
            } finally {
                partial.delete()
            }
        }

    protected fun githubGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", userAgent())
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    protected abstract fun userAgent(): String

    private companion object {
        /** Un instalador no debería pasar de esto; si pasa, algo va mal. */
        const val MAX_DOWNLOAD_BYTES = 1_500L * 1024 * 1024
    }
}
