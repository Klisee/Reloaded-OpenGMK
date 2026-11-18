package com.klisee.reloaded.gmk.update

/** En qué punto está la comprobación o la descarga de una actualización. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    data class Available(val release: GitHubRelease) : UpdateState

    data class Downloading(val release: GitHubRelease, val percent: Int) : UpdateState

    /** Ya está en disco y verificado; [installing] mientras el sistema abre el instalador. */
    data class Ready(val release: GitHubRelease, val path: String, val installing: Boolean = false) : UpdateState

    data class Failed(val reason: UpdateFailure, val release: GitHubRelease? = null) : UpdateState
}

/** Qué falló, para poder decírselo al usuario sin enseñar la traza. */
enum class UpdateFailure {
    Check,
    NoAsset,
    Download,
    Digest,
    Install,
}

/** Lo que se le enseña al usuario cuando algo falla. */
fun UpdateFailure.message(): String = when (this) {
    UpdateFailure.Check -> "No pude consultar GitHub. Revisa tu conexión."
    UpdateFailure.NoAsset -> "La versión nueva no trae un instalador para este dispositivo."
    UpdateFailure.Download -> "La descarga se cortó o el servidor no respondió."
    UpdateFailure.Digest -> "El archivo descargado no coincide con el que publicó GitHub. Se borró por seguridad."
    UpdateFailure.Install -> "No pude abrir el instalador del sistema."
}

/**
 * Ajustes del actualizador. Se guardan aparte del resto para no mezclar con `SettingsStore`,
 * que es común a todas las plataformas y ya es bastante grande.
 */
interface UpdateSettings {
    /** Comprobar sola al arrancar (como mucho una vez por hora). */
    var autoCheck: Boolean

    /** Canal: `stable` o `beta`. */
    var channel: String

    /** Avisar con una notificación del sistema cuando hay algo nuevo. */
    var notifications: Boolean

    /** Última comprobación que terminó bien, en milisegundos. */
    var lastCheckAt: Long

    /** Último intento, para no machacar la API si falla. */
    var lastAttemptAt: Long

    /** Las releases ya consultadas, en JSON, para enseñar el historial sin red. */
    var cachedReleasesJson: String

    /** Clave de la última versión avisada por notificación, para no repetirla. */
    var lastNotifiedKey: String
}

/** Cada cuánto se vuelve a mirar GitHub por iniciativa propia. */
const val UPDATE_CHECK_INTERVAL_MS: Long = 60 * 60 * 1_000L

/** Dice si toca comprobar sin que el usuario lo pida. */
fun updateCheckIsDue(lastAttemptAt: Long, now: Long): Boolean =
    lastAttemptAt !in 1..now || now - lastAttemptAt >= UPDATE_CHECK_INTERVAL_MS

/** Reloj en milisegundos; cada plataforma lo resolvió a su manera. */
expect fun nowMillis(): Long
