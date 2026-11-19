package com.klisee.reloaded.gmk.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Lo que cada plataforma tiene que saber hacer para que el actualizador funcione. Toda la lógica
 * (qué versión toca, cuándo comprobar, qué hacer al fallar) vive aquí, en común; los `actual` solo
 * hacen las tres cosas que dependen del sistema: hablar con GitHub, escribir el archivo y abrirlo.
 */
interface UpdatePlatformBridge {
    val platform: UpdatePlatform

    /** Versión instalada, como la publica GitHub (`0.6.7`). */
    val installedVersion: String

    /** Cuerpo de `GET https://api.github.com/repos/.../releases?per_page=50`. */
    suspend fun fetchReleases(): String

    /**
     * Descarga [asset] en la carpeta temporal y comprueba tamaño y SHA-256 mientras escribe.
     * Devuelve la ruta del archivo ya verificado. Cualquier fallo debe lanzar.
     */
    suspend fun download(asset: GitHubAsset, onProgress: (Int) -> Unit): String

    /** Abre el archivo descargado con el instalador del sistema. */
    suspend fun install(path: String): Boolean
}

/**
 * Comprueba y descarga actualizaciones desde las releases de GitHub.
 *
 * El orden es siempre el mismo: se consultan las releases (con caché de una hora), se elige la más
 * nueva que tenga instalador para esta plataforma, y solo entonces se descarga. La descarga pasa por
 * una verificación SHA-256 contra el digest que publica GitHub: si el archivo no coincide, se borra
 * y no se ofrece instalar. Es lo que evita instalar algo manipulado por el camino.
 */
class UpdateManager(
    private val bridge: UpdatePlatformBridge,
    private val settings: UpdateSettings,
    private val userAgent: String,
) {
    /** Los ajustes, para que la pantalla pueda leerlos y cambiarlos. */
    val updateSettings: UpdateSettings get() = settings

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val mutableHistory = MutableStateFlow<List<GitHubRelease>>(emptyList())
    private val mutableProgress = MutableStateFlow<Int?>(null)

    val state: StateFlow<UpdateState> = mutableState.asStateFlow()
    val history: StateFlow<List<GitHubRelease>> = mutableHistory.asStateFlow()
    val progress: StateFlow<Int?> = mutableProgress.asStateFlow()

    val currentVersion: String get() = bridge.installedVersion
    val platform: UpdatePlatform get() = bridge.platform

    /** Historial guardado de la última vez, para poder enseñarlo sin conexión. */
    fun cachedHistory(): List<GitHubRelease> = runCatching {
        json.decodeFromString<List<GitHubRelease>>(settings.cachedReleasesJson)
    }.getOrDefault(emptyList())

    /**
     * Mira si hay algo nuevo. Si [automatic] es `true` respeta la caché de una hora y no falla en
     * voz alta cuando no hay red: simplemente deja lo que ya había.
     */
    suspend fun check(automatic: Boolean = false): UpdateState {
        val now = nowMillis()
        if (automatic && !updateCheckIsDue(settings.lastAttemptAt, now)) {
            mutableHistory.value = cachedHistory()
            val cached = pickUpdate(cachedHistory(), currentVersion, platform, settings.channel)
            return (cached?.let(UpdateState::Available) ?: UpdateState.UpToDate(settings.lastCheckAt))
                .also { mutableState.value = it }
        }

        mutableState.value = UpdateState.Checking
        settings.lastAttemptAt = now
        return try {
            val releases = json.decodeFromString<List<GitHubRelease>>(bridge.fetchReleases())
            val history = releaseHistory(releases, settings.channel)
            mutableHistory.value = history
            settings.cachedReleasesJson = json.encodeToString(history)
            settings.lastCheckAt = now
            val update = pickUpdate(releases, currentVersion, platform, settings.channel)
            if (update == null) {
                mutableState.value = UpdateState.UpToDate(now)
            } else {
                if (update.assetFor(platform) == null) {
                    mutableState.value = UpdateState.Failed(UpdateFailure.NoAsset, update)
                } else {
                    mutableState.value = UpdateState.Available(update)
                }
            }
            mutableState.value
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val cached = if (automatic) null else pickUpdate(cachedHistory(), currentVersion, platform, settings.channel)
            mutableState.value = cached?.let(UpdateState::Available)
                ?: UpdateState.Failed(UpdateFailure.Check)
            mutableState.value
        }
    }

    /** Descarga y verifica el asset. Al terminar deja [UpdateState.Ready] con la ruta en disco. */
    suspend fun download(release: GitHubRelease): UpdateState {
        val asset = release.assetFor(platform)
        if (asset == null) {
            return UpdateState.Failed(UpdateFailure.NoAsset, release).also { mutableState.value = it }
        }
        mutableProgress.value = 0
        mutableState.value = UpdateState.Downloading(release, 0)
        return try {
            val path = bridge.download(asset) { percent ->
                mutableProgress.value = percent
                mutableState.value = UpdateState.Downloading(release, percent)
            }
            UpdateState.Ready(release, path)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            UpdateState.Failed(classify(error), release)
        }.also {
            mutableProgress.value = null
            mutableState.value = it
        }
    }

    /** Abre el instalador del sistema para el archivo ya descargado. */
    suspend fun install(ready: UpdateState.Ready): UpdateState {
        val installing = ready.copy(installing = true)
        mutableState.value = installing
        return try {
            if (!bridge.install(ready.path)) UpdateState.Failed(UpdateFailure.Install, ready.release)
            else ready.copy(installing = false)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            UpdateState.Failed(UpdateFailure.Install, ready.release)
        }.also { mutableState.value = it }
    }

    /** Descarga e instala de una vez, que es lo que hace el botón principal. */
    suspend fun downloadAndInstall(release: GitHubRelease): UpdateState {
        val downloaded = download(release)
        return if (downloaded is UpdateState.Ready) install(downloaded) else downloaded
    }

    /**
     * Distingue un fallo de red de uno de verificación. La diferencia importa: si el SHA-256 no
     * cuadra, el archivo se descartó a propósito y hay que decírselo al usuario.
     */
    private fun classify(error: Throwable): UpdateFailure =
        if (error is DigestMismatch) UpdateFailure.Digest else UpdateFailure.Download
}

/** El archivo descargado no coincide con el SHA-256 publicado. */
class DigestMismatch(message: String) : Exception(message)
