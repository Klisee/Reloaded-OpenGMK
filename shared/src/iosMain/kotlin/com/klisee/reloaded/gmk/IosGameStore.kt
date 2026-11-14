package com.klisee.reloaded.gmk

import com.klisee.reloaded.gmk.ios.gm_ios_configure
import com.klisee.reloaded.gmk.ios.gm_ios_has_game
import com.klisee.reloaded.gmk.ios.gm_ios_import
import com.klisee.reloaded.gmk.ios.gm_ios_saves
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosPickedFile(private val path: String) : PickedFile {
    override val name: String get() = path.substringAfterLast('/')

    override fun readBytes(): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        val size = data.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return bytes
    }

    fun path(): String = path
}

@OptIn(ExperimentalForeignApi::class)
class IosGameStore(
    private val documents: String = documentsDirectory(),
    private val root: String = "$documents/ReloadedGame",
) : GameStore {
    init {
        memScoped { gm_ios_configure(documents.cstr.ptr) }
        prepare()
    }

    private fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    override fun status(): GameStatus {
        val hasGame = memScoped { gm_ios_has_game(root.cstr.ptr) }
        return if (hasGame) GameStatus.Ready("Reloaded", null, 0L, 0L) else GameStatus.Missing
    }

    override fun prepare() {
        if (!exists(root)) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    override suspend fun importZip(
        file: PickedFile,
        onProgress: ((Int) -> Unit)?,
        onLog: ((String) -> Unit)?,
    ): Result<Unit> = runCatching {
        val path = (file as? IosPickedFile)?.path() ?: error("Archivo iOS no válido.")
        onLog?.invoke("Importando ${file.name}…")
        val result = memScoped { gm_ios_import(path.cstr.ptr, root.cstr.ptr) }
        if (result != 0) error("El motor no pudo importar el ZIP.")
        onProgress?.invoke(100)
        onLog?.invoke("Juego instalado.")
    }

    override suspend fun deleteGame(): Result<Unit> = runCatching {
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    override fun saveSlots(): List<SaveSlot> = emptyList()

    override fun importSlot(file: PickedFile, index: Int): Result<Int> = runCatching {
        val path = (file as? IosPickedFile)?.path() ?: error("Archivo iOS no válido.")
        val result = memScoped { gm_ios_saves(root.cstr.ptr, path.cstr.ptr, index.toUInt(), true) }
        if (result != 0) error("El motor no pudo importar la partida.")
        index
    }

    override fun deleteSlot(index: Int): Result<Unit> =
        Result.failure(IllegalStateException("Borrar partidas desde iOS todavía no está conectado."))

    override fun exportSlot(index: Int): Result<PickedFile> = runCatching {
        val output = "${NSTemporaryDirectory()}partida$index.zip"
        val result = memScoped { gm_ios_saves(root.cstr.ptr, output.cstr.ptr, index.toUInt(), false) }
        if (result != 0) error("El motor no pudo exportar la partida.")
        IosPickedFile(output)
    }

    override fun exportSaves(): Result<PickedFile> = runCatching {
        val output = "${NSTemporaryDirectory()}partidas.zip"
        val result = memScoped { gm_ios_saves(root.cstr.ptr, output.cstr.ptr, 0u, false) }
        if (result != 0) error("El motor no pudo exportar las partidas.")
        IosPickedFile(output)
    }

    override fun importSaves(file: PickedFile): Result<Int> = runCatching {
        val path = (file as? IosPickedFile)?.path() ?: error("Archivo iOS no válido.")
        val result = memScoped { gm_ios_saves(root.cstr.ptr, path.cstr.ptr, 0u, true) }
        if (result != 0) error("El motor no pudo importar las partidas.")
        0
    }

    override fun modsEnabled(): Boolean = false
    override fun mods(): List<ModEntry> = emptyList()
    override fun setModsEnabled(enabled: Boolean) = Unit
    override fun importMod(file: PickedFile): Result<String> =
        Result.failure(IllegalStateException("Los mods no están disponibles en iOS."))
    override fun setModEnabled(entry: ModEntry, enabled: Boolean) = Unit

    override fun logText(): String {
        val data = NSData.dataWithContentsOfFile("$documents/emulator.log") ?: return ""
        val size = data.length.toInt()
        if (size <= 0) return ""
        val bytes = ByteArray(size)
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        return bytes.decodeToString()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): String =
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: NSTemporaryDirectory()
