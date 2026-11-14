package com.klisee.reloaded.gmk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile

class GameRepository(private val root: File) {

    sealed interface Status {
        data object Missing : Status
        data class Ready(
            val exe: String,
            val version: String?,
            val installedAt: Long,
            val sizeBytes: Long,
        ) : Status
    }

    val gameDir: File get() = File(root, "game")
    val logFile: File get() = File(root, "app.log")
    private val metaFile: File get() = File(root, "game.json")
    private val saveFolders = listOf("partidas", "equipos", "char_custom")

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

    fun status(): Status {
        val exe = findExe(gameDir) ?: return Status.Missing
        val data = File(gameDir, "data")
        if (!data.isDirectory || (data.listFiles()?.size ?: 0) < 5) return Status.Missing
        val lines = runCatching { metaFile.readLines() }.getOrDefault(emptyList())
        val values = lines.mapNotNull { line ->
            val key = line.substringBefore('=', "").trim()
            if (key.isEmpty()) null else key to line.substringAfter('=', "").trim()
        }.toMap()
        val storedSize = values["sizeBytes"]?.toLongOrNull()?.takeIf { it > 0L }
        val sizeBytes = storedSize ?: runCatching {
            gameDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
        val storedInstalledAt = values["installedAt"]?.toLongOrNull()?.takeIf { it > 0L }
        val installedAt = storedInstalledAt ?: exe.lastModified().takeIf { it > 0L }
            ?: gameDir.lastModified().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        if (storedSize == null || storedInstalledAt == null) {
            runCatching {
                metaFile.writeText("installedAt=$installedAt\nsizeBytes=$sizeBytes\n")
            }
        }
        return Status.Ready(
            exe = exe.name,
            version = versionFromName(exe.name),
            installedAt = installedAt,
            sizeBytes = sizeBytes,
        )
    }

    fun prepare() {
        root.mkdirs()
        gameDir.mkdirs()
        gameDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("exe", true) && it.name.contains("online", true) }
            .forEach { it.delete() }
    }

    fun gameExe(): File? = findExe(gameDir)

    data class ModEntry(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val enabled: Boolean,
        val manifest: File,
    )

    fun modsEnabled(): Boolean {
        val mods = File(gameDir, "mods")
        return File(mods, "enabled.flag").isFile && !File(mods, "disabled.flag").isFile
    }

    fun setModsEnabled(enabled: Boolean) {
        val mods = File(gameDir, "mods").apply { mkdirs() }
        val enabledFlag = File(mods, "enabled.flag")
        val disabledFlag = File(mods, "disabled.flag")
        if (enabled) {
            disabledFlag.delete()
            enabledFlag.writeText("1")
        } else {
            enabledFlag.delete()
            disabledFlag.writeText("1")
        }
    }

    fun mods(): List<ModEntry> {
        val dir = File(gameDir, "mods")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "mod.ini").isFile }
            .sortedBy { it.name.lowercase() }
            .map { folder ->
                val manifest = File(folder, "mod.ini")
                val values = manifest.readLines().mapNotNull { line ->
                    val text = line.trim()
                    if (text.isEmpty() || text.startsWith("#") || text.startsWith(";") || !text.contains('=')) null
                    else text.substringBefore('=').trim().lowercase() to text.substringAfter('=').trim()
                }.toMap()
                ModEntry(
                    id = folder.name,
                    name = values["name"].orEmpty().ifEmpty { folder.name },
                    version = values["version"].orEmpty(),
                    author = values["author"].orEmpty(),
                    description = values["description"].orEmpty(),
                    enabled = values["enabled"].orEmpty().lowercase().let { it != "0" && it != "false" && it != "no" && it != "off" },
                    manifest = manifest,
                )
            }
    }

    fun setModEnabled(entry: ModEntry, enabled: Boolean) {
        val lines = entry.manifest.readLines().toMutableList()
        val index = lines.indexOfFirst { it.substringBefore('=').trim().equals("enabled", ignoreCase = true) }
        val value = if (enabled) "1" else "0"
        if (index >= 0) lines[index] = "enabled=$value" else lines.add("enabled=$value")
        entry.manifest.writeText(lines.joinToString("\n") + "\n")
    }

    fun importMod(zip: File): Result<String> = runCatching {
        val mods = File(gameDir, "mods").apply { mkdirs() }
        val staging = File(root, "mod.staging").apply { deleteRecursively(); mkdirs() }
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.split('/').any { it == ".." }) error("El zip trae una ruta no permitida.")
                val target = File(staging, name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        val rootDir = if (File(staging, "mod.ini").isFile) staging else staging.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "mod.ini").isFile }.singleOrNull()
            ?: error("El zip debe contener un mod.ini.")
        val id = (if (rootDir == staging) zip.nameWithoutExtension else rootDir.name)
            .replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "mod" }
        val destination = File(mods, id)
        destination.deleteRecursively()
        rootDir.copyRecursively(destination, overwrite = true)
        staging.deleteRecursively()
        id
    }

    fun saveSlots(): List<SaveSlot> {
        val folder = File(gameDir, "data/partidas")
        val pattern = Regex("^reloaded(\\d+)\\.[a-z]+$", RegexOption.IGNORE_CASE)
        val used = folder.listFiles().orEmpty()
            .mapNotNull { pattern.find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .toSet()
        return (1..maxOf(3, used.maxOrNull() ?: 0)).map { index ->
            if (index !in used) return@map SaveSlot(index, null, null, null, null)
            val lines = runCatching { File(folder, "reloaded$index.nfo").readLines() }
                .getOrDefault(emptyList())
                .filter { it.isNotBlank() }
            fun field(at: Int) = lines.getOrNull(at)?.trim()?.takeIf { it.isNotEmpty() }?.let(::decodeGameText)
            fun intField(at: Int) = lines.getOrNull(at)?.trim()?.toIntOrNull()
            SaveSlot(index, field(0), field(1), field(2), field(3), intField(4), intField(5))
        }
    }

    fun slotArt(slot: SaveSlot): SlotArt = slotArt(slot.spriteId, slot.pkmnId)

    fun slotArt(spriteId: Int?, pkmnId: Int?): SlotArt {
        fun art(name: String): String? {
            val file = File(gameDir, "data/cache/ui/$name")
            return if (file.isFile && file.canRead()) file.absolutePath else null
        }
        return SlotArt(
            portraitPath = spriteId?.let { art("prota_$it.png") },
            pkmnPath = pkmnId?.let { art("pkmn_$it.png") },
        )
    }

    fun deleteSlot(index: Int): Result<Unit> = runCatching {
        val folder = File(gameDir, "data/partidas")
        val files = folder.listFiles().orEmpty()
            .filter { it.name.startsWith("reloaded$index.") || it.name.startsWith("reloaded${index}_") }
        if (files.isEmpty()) error("Esa partida ya no está.")
        files.forEach { it.delete() }
    }

    fun exportSlot(index: Int): Result<File> = runCatching {
        val folder = File(gameDir, "data/partidas")
        val files = folder.listFiles().orEmpty().filter { it.name.startsWith("reloaded$index.") }
        if (files.isEmpty()) error("Esa partida está vacía.")
        val out = File(root, "partida$index.zip")
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(java.util.zip.ZipEntry("partidas/" + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        out
    }

    fun importSlot(zip: File, index: Int): Result<Int> = runCatching {
        val folder = File(gameDir, "data/partidas")
        if (!folder.isDirectory) error("Primero instala el juego.")
        val numbered = Regex("^reloaded\\d+(\\.|_)(.*)$", RegexOption.IGNORE_CASE)
        var written = 0
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                val match = numbered.find(name)
                if (!entry.isDirectory && entry.name.startsWith("partidas/") && match != null) {
                    val target = File(folder, "reloaded$index${match.groupValues[1]}${match.groupValues[2]}")
                    target.outputStream().use { input.copyTo(it) }
                    written++
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        if (written == 0) error("Ese zip no trae ninguna partida.")
        written
    }

    fun exportSaves(): Result<File> = runCatching {
        val data = File(gameDir, "data")
        val out = File(root, "partidas.zip")
        var written = 0
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
            saveFolders.forEach { folder ->
                val base = File(data, folder)
                if (!base.isDirectory) return@forEach
                base.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(java.util.zip.ZipEntry("$folder/" + file.relativeTo(base).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    written++
                }
            }
        }
        if (written == 0) error("No hay partidas que exportar.")
        out
    }

    fun importSaves(zip: File): Result<Int> = runCatching {
        val data = File(gameDir, "data")
        if (!data.isDirectory) error("Primero instala el juego.")
        var files = 0
        var saves = 0
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name
                val target = File(data, name)
                val wanted = saveFolders.any { name.startsWith("$it/") }
                val safe = target.canonicalPath.startsWith(data.canonicalPath + File.separator)
                if (!entry.isDirectory && wanted && safe) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                    files++
                    if (name.startsWith("partidas/") && name.endsWith(".savc", true) && !name.contains("_bk", true)) saves++
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        if (files == 0) error("Ese zip no trae partidas del juego.")
        saves
    }

    suspend fun deleteGame(): Unit = withContext(Dispatchers.IO) {
        gameDir.deleteRecursively()
        metaFile.delete()
    }

    suspend fun importZip(
        zip: File,
        onProgress: (Int) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prepare()
            val staging = File(root, "game.staging")
            val previous = File(root, "game.old")
            staging.deleteRecursively()
            previous.deleteRecursively()

            val plan = planZip(zip)
            onLog("Zip válido: ${plan.files.size} archivos.")
            val saved = File(root, "game.saves")
            saved.deleteRecursively()
            if (gameDir.isDirectory) {
                saveFolders.forEach { folder ->
                    val from = File(gameDir, "data/$folder")
                    if (from.isDirectory) from.copyRecursively(File(saved, folder), overwrite = true)
                }
            }
            staging.mkdirs()

            var written = 0L
            openZip(zip).use { archive ->
                for (item in plan.files) {
                    val target = File(staging, item.relative)
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        error("El zip trae una ruta no válida: ${item.relative}")
                    }
                    if (item.directory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    archive.getInputStream(item.entry).use { input ->
                        target.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    written += target.length()
                    val percent = if (plan.totalBytes > 0) ((written * 100) / plan.totalBytes).toInt() else 0
                    onProgress(percent.coerceIn(0, 99))
                }
            }

            staging.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("exe", true) && it.name.contains("online", true) }
                .forEach { it.delete() }
            if (findExe(staging) == null || !File(staging, "data").isDirectory) {
                error("El zip no trae el .exe y la carpeta data/ en la misma carpeta.")
            }

            val hadGame = gameDir.isDirectory
            if (hadGame && !gameDir.renameTo(previous)) error("No pude apartar el juego anterior.")
            if (!staging.renameTo(gameDir)) {
                if (hadGame) {
                    gameDir.deleteRecursively()
                    previous.renameTo(gameDir)
                }
                error("No pude instalar el juego nuevo.")
            }
            previous.deleteRecursively()
            saveFolders.forEach { folder ->
                val from = File(saved, folder)
                if (from.isDirectory) from.copyRecursively(File(gameDir, "data/$folder"), overwrite = false)
            }
            saved.deleteRecursively()

            metaFile.writeText(
                listOf(
                    "installedAt=${System.currentTimeMillis()}",
                    "sizeBytes=$written",
                ).joinToString("\n"),
            )
            onProgress(100)
            onLog("Juego instalado en ${gameDir.absolutePath} (${formatBytes(written)}).")
        }
    }

    /**
     * Some Reloaded ZIPs were made with a legacy code page and have no UTF-8 flag on their
     * entries. Java 17+ rejects those central directories, while CP437 reads them correctly.
     */
    private fun openZip(file: File): ZipFile =
        try {
            ZipFile(file)
        } catch (error: ZipException) {
            ZipFile(file, Charset.forName("Cp437"))
        }

    private data class ZipItem(
        val entry: java.util.zip.ZipEntry,
        val relative: String,
        val directory: Boolean,
    )

    private data class ZipPlan(
        val files: List<ZipItem>,
        val totalBytes: Long,
    )

    private fun planZip(zip: File): ZipPlan {
        if (!zip.isFile || zip.length() == 0L) error("El archivo del juego está vacío.")
        openZip(zip).use { archive ->
            val entries = archive.entries().toList()
            val names = entries.map { it.name.replace('\\', '/') }
            val roots = linkedSetOf("")
            names.forEach { name ->
                val slash = name.indexOf('/')
                if (slash > 0) roots += name.substring(0, slash + 1)
            }
            val root = roots.firstOrNull { prefix ->
                val hasExe = names.any { name ->
                    name.startsWith(prefix, true) &&
                        !name.endsWith("/") &&
                        name.substring(prefix.length).let { it.endsWith(".exe", true) && '/' !in it }
                }
                val hasData = names.any { name -> name.startsWith("${prefix}data/", true) }
                hasExe && hasData
            } ?: error("El zip no parece Pokémon Reloaded: falta el .exe y data/.")

            val files = entries.mapNotNull { entry ->
                val name = entry.name.replace('\\', '/')
                if (!name.startsWith(root) || name.length == root.length) return@mapNotNull null
                val relative = name.substring(root.length)
                if (relative.startsWith("__MACOSX/") || relative.endsWith(".DS_Store")) return@mapNotNull null
                ZipItem(entry, relative, entry.isDirectory || name.endsWith("/"))
            }
            if (files.isEmpty()) error("El zip no tiene archivos que instalar.")
            return ZipPlan(files, files.sumOf { if (it.directory) 0L else it.entry.size.coerceAtLeast(0L) })
        }
    }

    private fun findExe(dir: File): File? {
        val exes = dir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("exe", true) }
        return exes.firstOrNull { !it.name.contains("online", ignoreCase = true) } ?: exes.firstOrNull()
    }

    private fun versionFromName(name: String): String? =
        Regex("""\d+\.\d+(?:\.\d+){1,2}""").find(name)?.value

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
