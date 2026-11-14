package com.klisee.reloaded.gmk

import java.io.File

class FilePickedFile(val file: File) : PickedFile {
    override val name: String get() = file.name
    override fun readBytes(): ByteArray = file.readBytes()
}

fun PickedFile.requireJvmFile(): File =
    (this as? FilePickedFile)?.file ?: error("Archivo no válido en JVM: $name")

class JvmGameStore(private val delegate: GameRepository) : GameStore {
    override fun status(): GameStatus = when (val status = delegate.status()) {
        GameRepository.Status.Missing -> GameStatus.Missing
        is GameRepository.Status.Ready -> GameStatus.Ready(
            status.exe,
            status.version,
            status.installedAt,
            status.sizeBytes,
        )
    }

    override fun prepare() = delegate.prepare()

    override suspend fun importZip(
        file: PickedFile,
        onProgress: ((Int) -> Unit)?,
        onLog: ((String) -> Unit)?,
    ): Result<Unit> = delegate.importZip(
        file.requireJvmFile(),
        onProgress = onProgress ?: {},
        onLog = onLog ?: {},
    )

    override suspend fun deleteGame(): Result<Unit> = runCatching { delegate.deleteGame() }

    override fun saveSlots(): List<SaveSlot> = delegate.saveSlots().map {
        SaveSlot(it.index, it.name, it.seen, it.caught, it.place, it.spriteId, it.pkmnId)
    }

    override fun slotArt(slot: SaveSlot): SlotArt {
        val art = delegate.slotArt(slot.spriteId, slot.pkmnId)
        return SlotArt(art.portraitPath, art.pkmnPath)
    }

    override fun importSlot(file: PickedFile, index: Int): Result<Int> =
        delegate.importSlot(file.requireJvmFile(), index)

    override fun deleteSlot(index: Int): Result<Unit> = delegate.deleteSlot(index)

    override fun exportSlot(index: Int): Result<PickedFile> =
        delegate.exportSlot(index).map(::FilePickedFile)

    override fun exportSaves(): Result<PickedFile> =
        delegate.exportSaves().map(::FilePickedFile)

    override fun importSaves(file: PickedFile): Result<Int> =
        delegate.importSaves(file.requireJvmFile())

    override fun modsEnabled(): Boolean = delegate.modsEnabled()

    override fun mods(): List<ModEntry> = delegate.mods().map {
        ModEntry(it.id, it.name, it.version, it.author, it.description, it.enabled)
    }

    override fun setModsEnabled(enabled: Boolean) = delegate.setModsEnabled(enabled)

    override fun importMod(file: PickedFile): Result<String> =
        delegate.importMod(file.requireJvmFile())

    override fun setModEnabled(entry: ModEntry, enabled: Boolean) {
        val current = delegate.mods().firstOrNull { it.id == entry.id } ?: return
        delegate.setModEnabled(current, enabled)
    }

    override fun logText(): String =
        runCatching { delegate.logFile.readText() }.getOrDefault("No pude leer los logs.")
}


actual suspend fun latestBlogPost(): Result<BlogPost?> =
    Blog.latest().map { post ->
        BlogPost(
            title = post.title,
            published = post.published,
            summary = post.summary,
            postUrl = post.postUrl,
            images = post.images,
            version = post.version,
            links = post.links.map { BlogLink(it.label, it.url) },
            publishedMillis = post.published?.let {
                runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }.getOrNull()
            },
        )
    }

actual suspend fun checkForUpdate(installed: String, abis: List<String>): Result<UpdateRelease?> =
    Update.check(installed, abis).map { release ->
        release?.let { UpdateRelease(it.version, it.pageUrl) }
    }

actual fun formatDate(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

actual fun readLog(repo: GameStore): String {
    val logcat = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "brief", "-t", "1500")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.trim()
    }.getOrNull()
    val kept = runCatching { repo.logText().takeLast(32_000) }.getOrNull()
    if (!logcat.isNullOrEmpty()) {
        return if (kept.isNullOrBlank()) logcat else "$logcat\n\n--- de antes de reiniciar ---\n$kept"
    }
    return kept?.takeLast(64_000)?.ifBlank { "Todavía no hay logs. Juega una vez para generarlos." }
        ?: "No pude leer los logs."
}

