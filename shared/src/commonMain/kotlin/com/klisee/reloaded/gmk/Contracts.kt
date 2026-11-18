package com.klisee.reloaded.gmk

import androidx.compose.runtime.Composable

interface PickedFile {
    val name: String
    fun readBytes(): ByteArray
}

sealed interface GameStatus {
    data object Missing : GameStatus
    data class Ready(
        val exe: String,
        val version: String?,
        val installedAt: Long,
        val sizeBytes: Long,
    ) : GameStatus
}

data class SaveSlot(
    val index: Int,
    val name: String?,
    val seen: String?,
    val caught: String?,
    val place: String?,
    val spriteId: Int? = null,
    val pkmnId: Int? = null,
)

data class SlotArt(
    val portraitPath: String? = null,
    val pkmnPath: String? = null,
)

data class ModEntry(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
)

interface SettingsStore {
    var onboardingDone: Boolean
    var theme: String
    /// Modo de color: "auto" (sigue al sistema), "light" u "dark".
    ///
    /// Es independiente de `theme`, que elige el estilo visual (Material You o Liquid Glass).
    /// Antes solo existía `theme` y el claro/oscuro se deducía del sistema; en escritorio la
    /// detección fallaba siempre y la app se quedaba en claro.
    var darkMode: String
    var language: String
    var fpsMode: String
    var touchStyle: String
    var moveMode: String
    var touchScale: Float
    var touchOpacity: Float
    var keepAspect: Boolean
    var showFps: Boolean
    /// Backend de renderizado elegido por el jugador.
    ///
    /// `opengl` es el backend histórico, probado en todas las plataformas. `wgpu` es el backend
    /// nuevo: sobre él se puede pedir una API concreta (DirectX 12, Vulkan o OpenGL por wgpu) con
    /// `OPENGMK_WGPU_BACKEND`. Cuando la API no se fija, wgpu elige por rendimiento.
    var renderer: String
    var modsEnabled: Boolean
    var controlBindings: Map<ControlAction, Int>
    var stickEnabled: Boolean
    var keepScreenOn: Boolean
    var touchLayouts: Map<String, TouchPlacement>
    var touchAppearance: TouchAppearance
    var touchSprites: Map<String, String>

    fun writeControls(bindings: Map<ControlAction, Int>, stick: Boolean, padConnected: Boolean)
    fun stageSprite(id: String, bytes: ByteArray): String
    fun resetSprite(id: String)
    fun clearSprites()
    fun writeText(name: String, text: String)
}

interface GameStore {
    fun status(): GameStatus
    fun prepare()
    suspend fun importZip(
        file: PickedFile,
        onProgress: ((Int) -> Unit)? = null,
        onLog: ((String) -> Unit)? = null,
    ): Result<Unit>

    suspend fun deleteGame(): Result<Unit>
    fun saveSlots(): List<SaveSlot>
    fun slotArt(slot: SaveSlot): SlotArt = SlotArt()
    fun importSlot(file: PickedFile, index: Int): Result<Int>
    fun deleteSlot(index: Int): Result<Unit>
    fun exportSlot(index: Int): Result<PickedFile>
    fun exportSaves(): Result<PickedFile>
    fun importSaves(file: PickedFile): Result<Int>
    fun modsEnabled(): Boolean
    fun mods(): List<ModEntry>
    fun setModsEnabled(enabled: Boolean)
    fun importMod(file: PickedFile): Result<String>
    fun setModEnabled(entry: ModEntry, enabled: Boolean)
    fun logText(): String
}

data class BlogLink(val label: String, val url: String)

data class BlogPost(
    val title: String,
    val published: String?,
    val summary: String,
    val postUrl: String?,
    val images: List<String>,
    val version: String?,
    val links: List<BlogLink> = emptyList(),
    val publishedMillis: Long? = null,
)

data class UpdateRelease(
    val version: String,
    val pageUrl: String,
)


@Composable
expect fun ControlsScreen(
    settings: SettingsStore,
    pads: List<String>,
    onBack: () -> Unit,
)


expect suspend fun latestBlogPost(): Result<BlogPost?>
expect suspend fun checkForUpdate(installed: String, abis: List<String>): Result<UpdateRelease?>
expect fun readLog(repo: GameStore): String
expect fun formatDate(millis: Long): String
expect fun platformLabel(): String

/// Etiqueta de la API que usa el backend wgpu, para mostrar en ajustes.
expect fun gpuBackendLabel(): String

/// Si el sistema está en modo oscuro.
///
/// `isSystemInDarkTheme()` de Compose no lee el tema de Windows en escritorio: devuelve siempre
/// claro. Esta función lo consulta de la forma nativa de cada plataforma y devuelve `null` cuando
/// no se puede saber, para que el llamador decida.
expect fun systemPrefersDark(): Boolean?

/// Nombre de la API de wgpu que se le pasa al motor en `OPENGMK_WGPU_BACKEND`.
///
/// El motor acepta `dx12`, `vulkan` y `gl`. Se expone como constante para que la pantalla de
/// ajustes y el lanzador no puedan desincronizarse.
const val WGPU_BACKEND_DX12 = "dx12"
const val WGPU_BACKEND_VULKAN = "vulkan"
const val WGPU_BACKEND_GL = "gl"


@Composable
expect fun TouchAssetsScreen(
    settings: SettingsStore,
    onPickImage: ((PickedFile?) -> Unit) -> Unit,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onBack: () -> Unit,
)

/**
 * Decodifica las secuencias de escape que el juego guarda en sus textos
 * (por ejemplo "Centro Pok&{e}mon" para los acentos). Las secuencias
 * desconocidas se dejan intactas para no perder información.
 */
fun decodeGameText(raw: String): String {
    if (!raw.contains("&{")) return raw
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val start = raw.indexOf("&{", i)
        if (start < 0) {
            out.append(raw, i, raw.length)
            break
        }
        out.append(raw, i, start)
        val close = raw.indexOf('}', start + 2)
        if (close < 0 || close - start > 8) {
            out.append("&{")
            i = start + 2
            continue
        }
        val code = raw.substring(start + 2, close)
        val upper = code != code.lowercase()
        val decoded = when (code.lowercase()) {
            "e" -> if (upper) "É" else "é"
            "a" -> if (upper) "Á" else "á"
            "i" -> if (upper) "Í" else "í"
            "o" -> if (upper) "Ó" else "ó"
            "u" -> if (upper) "Ú" else "ú"
            "n" -> if (upper) "Ñ" else "ñ"
            "u2" -> if (upper) "Ü" else "ü"
            "ie" -> "¡"
            "iq" -> "¿"
            "ao" -> "ª"
            "oo" -> "º"
            else -> null
        }
        if (decoded != null) {
            out.append(decoded)
        } else {
            out.append(raw, start, close + 1)
        }
        i = close + 1
    }
    return out.toString()
}
