package com.klisee.reloaded.gmk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.math.max

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

    /** Escribe una línea con marca de tiempo en `app.log`, para diagnóstico en todas las plataformas. */
    fun log(message: String) {
        ImportLog(logFile).line(message)
    }

    suspend fun importZip(
        zip: File,
        onProgress: (Int) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fileLog = ImportLog(logFile)
        fun say(message: String) {
            fileLog.line(message)
            runCatching { onLog(message) }
        }

        try {
            importZipInto(zip, ::say, onProgress)
            Result.success(Unit)
        } catch (error: kotlinx.coroutines.CancellationException) {
            fileLog.line("Importación cancelada.")
            throw error
        } catch (error: Throwable) {
            say("IMPORTACIÓN FALLIDA: ${error.javaClass.simpleName}: ${error.message}")
            fileLog.stackTrace(error)
            Result.failure(error)
        }
    }

    private fun importZipInto(zip: File, say: (String) -> Unit, onProgress: (Int) -> Unit) {
        // Un callback de la interfaz que falle no debe tumbar una importación ya correcta.
        fun progress(value: Int) {
            runCatching { onProgress(value.coerceIn(0, 100)) }
        }
        val staging = File(root, "game.staging")
        val previous = File(root, "game.old")
        root.mkdirs()
        staging.deleteRecursively()

        // Si un intento anterior murió después de apartar el juego, queda en game.old.
        if (previous.isDirectory && !looksInstalled(gameDir)) {
            say("Recuperando el juego anterior desde ${previous.name}.")
            gameDir.deleteRecursively()
            if (!moveDirectory(previous, gameDir, say)) {
                error("Había un juego anterior en ${previous.absolutePath} pero no pude restaurarlo.")
            }
        }
        val hadGame = gameDir.isDirectory
        previous.deleteRecursively()

        val saved = File(root, "game.saves")
        if (saved.isDirectory && hadGame) {
            // Restos de una importación anterior que no llegó a restaurar las partidas.
            var merged = 0
            for (folder in saveFolders) {
                val from = File(saved, folder)
                if (!from.isDirectory) continue
                runCatching { from.copyRecursively(File(gameDir, "data/$folder"), overwrite = false) }
                    .onSuccess { merged++ }
                    .onFailure { say("Aviso: no pude recuperar las partidas de $folder: ${it.message}") }
            }
            if (merged > 0) say("Partidas de una importación interrumpida recuperadas.")
            saved.deleteRecursively()
        } else if (saved.isDirectory) {
            say("Encontré partidas de una instalación anterior; se restaurarán al terminar.")
        }
        var movedAside = false
        var installed = false
        var keepSavesBackup = false

        say(
            "Importando ${zip.name} (${formatBytes(zip.length())}) en ${root.absolutePath}; " +
                "libre ${if (freeBytes() > 0) formatBytes(freeBytes()) else "desconocido"}, Android=${isAndroid()}",
        )

        try {
            val plan = planZip(zip, say)
            say(
                "Plan: ${plan.files.count { !it.directory }} archivos, " +
                    "${plan.files.count { it.directory }} carpetas, root=«${plan.root.ifEmpty { "(raíz)" }}», " +
                    "esperado ${formatBytes(plan.totalBytes)}" +
                    (if (plan.unknownSizes > 0) " (${plan.unknownSizes} entradas sin tamaño declarado)" else ""),
            )
            if (plan.skipped.isNotEmpty()) {
                say("Omitidas ${plan.skipped.size} entradas: ${plan.skipped.take(8).joinToString(", ")}")
            }
            if (plan.duplicates.isNotEmpty()) {
                say(
                    "Duplicadas ${plan.duplicates.size} entradas; gana la última: " +
                        plan.duplicates.take(8).joinToString(", "),
                )
            }
            if (plan.longNames.isNotEmpty()) {
                say("Nombres largos (posible truncado): ${plan.longNames.take(5).joinToString(", ")}")
            }

            val saveBytes = if (hadGame) {
                saveFolders.sumOf { folder ->
                    val from = File(gameDir, "data/$folder")
                    if (from.isDirectory) from.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
                }
            } else {
                0L
            }
            val required = plan.totalBytes + saveBytes + max(32L * 1024 * 1024, plan.totalBytes / 50)
            val free = freeBytes()
            say(
                "Espacio: necesito ~${formatBytes(required)} (juego ${formatBytes(plan.totalBytes)} + " +
                    "partidas ${formatBytes(saveBytes)} + margen), libre ${if (free > 0) formatBytes(free) else "desconocido"}.",
            )
            if (free > 0 && free < required) {
                error(
                    "No hay espacio suficiente: el juego necesita ~${formatBytes(required)} y solo quedan " +
                        "${formatBytes(free)} libres. Libera al menos ${formatBytes(required - free)} y reinténtalo.",
                )
            }

            if (hadGame) {
                for (folder in saveFolders) {
                    val from = File(gameDir, "data/$folder")
                    if (!from.isDirectory) continue
                    try {
                        from.copyRecursively(File(saved, folder), overwrite = true)
                    } catch (error: Throwable) {
                        throw IOException(
                            "No pude conservar tus partidas ($folder): " +
                                "${error.javaClass.simpleName}: ${error.message}",
                            error,
                        )
                    }
                }
                if (saved.isDirectory) {
                    val bytes = saved.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    say("Partidas guardadas aparte: ${formatBytes(bytes)} (se restauran al final).")
                }
            }

            if (!staging.mkdirs() && !staging.isDirectory) {
                error("No pude crear la carpeta temporal ${staging.absolutePath}.")
            }
            var written = 0L
            var filesWritten = 0
            val expectedFiles = plan.files.count { !it.directory }
            val allSizesKnown = plan.unknownSizes == 0
            val stagingBasePath = staging.canonicalPath + File.separator
            openZip(zip, plan.charset, say).use { archive ->
                for (item in plan.files) {
                    val target = File(staging, item.relative)
                    if (!target.canonicalPath.startsWith(stagingBasePath)) {
                        error("El zip trae una ruta fuera de la carpeta de instalación: ${item.relative}")
                    }
                    if (item.directory) {
                        if (!target.isDirectory && !target.mkdirs() && !target.isDirectory) {
                            say("Aviso: no pude crear la carpeta ${item.relative}")
                        }
                        continue
                    }
                    target.parentFile?.mkdirs()
                    val crc = CRC32()
                    var count = 0L
                    try {
                        archive.getInputStream(item.entry).use { raw ->
                            BufferedInputStream(raw).use { input ->
                                BufferedOutputStream(target.outputStream()).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        output.write(buffer, 0, read)
                                        crc.update(buffer, 0, read)
                                        count += read
                                        // Corta un zip que miente sobre el tamaño antes de llenar el disco.
                                        if (item.declaredStrict && count > item.expectedSize) {
                                            throw IOException(
                                                "«${item.relative}» produce más datos de los ${item.expectedSize} bytes declarados.",
                                            )
                                        }
                                        if (!allSizesKnown && written + count > plan.totalBytes * 2 + 64L * 1024 * 1024) {
                                            throw IOException(
                                                "El zip descomprime mucho más de lo esperado; se cancela por seguridad.",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        target.delete()
                        val detail = error.message.orEmpty()
                        val friendly = when {
                            detail.contains("encrypted", true) ->
                                "la entrada está cifrada; el zip necesita contraseña."
                            detail.contains("No space", true) || detail.contains("ENOSPC", true) ->
                                "no queda espacio en el almacenamiento interno."
                            else -> "${error.javaClass.simpleName}${if (detail.isEmpty()) "" else ": $detail"}"
                        }
                        throw IOException("Zip dañado en «${item.relative}»: $friendly", error)
                    }
                    if (item.declaredStrict && count != item.expectedSize) {
                        target.delete()
                        throw IOException(
                            "«${item.relative}» se extrajo incompleto: $count de ${item.expectedSize} bytes.",
                        )
                    }
                    if (!item.declaredStrict && item.entry.size == 0L && count > 0) {
                        say("Aviso: «${item.relative}» declaraba 0 bytes pero ocupa ${formatBytes(count)}; se acepta.")
                    }
                    if (item.declaredStrict && item.entry.crc >= 0 && crc.value != item.entry.crc) {
                        target.delete()
                        throw IOException(
                            "«${item.relative}» está corrupto (CRC ${crc.value} != ${item.entry.crc}).",
                        )
                    }
                    written += count
                    filesWritten++
                    val percent = if (allSizesKnown && plan.totalBytes > 0) {
                        ((written * 100) / plan.totalBytes).toInt()
                    } else {
                        (filesWritten * 100) / max(1, expectedFiles)
                    }
                    progress(percent.coerceAtMost(99))
                }
            }

            say(
                "Extraídos $filesWritten archivos (${formatBytes(written)}); " +
                    "plan ${formatBytes(plan.totalBytes)}.",
            )
            if (allSizesKnown && plan.totalBytes > 0 && written != plan.totalBytes) {
                throw IOException(
                    "La extracción no cuadra con el plan: se escribieron ${formatBytes(written)} " +
                        "de ${formatBytes(plan.totalBytes)} previstos. El zip está incompleto o dañado.",
                )
            }
            if (filesWritten != expectedFiles) {
                error(
                    "La extracción quedó incompleta: $filesWritten archivos de " +
                        "$expectedFiles previstos.",
                )
            }

            normalizeStaging(staging, say)

            val exe = findExe(staging)
                ?: throw IOException(
                    "El zip no trae el .exe en la carpeta del juego. " +
                        "Contenido: ${staging.list()?.take(10)?.joinToString(", ").orEmpty()}",
                )
            val data = File(staging, "data")
            if (!data.isDirectory) error("El zip no trae la carpeta data/ junto al ejecutable (${exe.name}).")
            val dataEntries = data.listFiles()?.size ?: 0
            if (dataEntries < 5) {
                say("Aviso: data/ solo tiene $dataEntries entradas; puede que el zip esté incompleto.")
            }
            val extracted = countFiles(staging)
            if (extracted != filesWritten) {
                error("Tras ordenar la carpeta hay $extracted archivos, pero se escribieron $filesWritten.")
            }
            if (exe.length() == 0L) {
                say("Aviso: el ejecutable «${exe.name}» está vacío (0 bytes); puede que el zip esté dañado.")
            }
            val dataFiles = data.walkTopDown().count { it.isFile }
            if (dataFiles == 0) error("La carpeta data/ del zip está vacía; no parece el juego completo.")
            say(
                "Verificado: ${exe.name} (${formatBytes(exe.length())}) + data/ con $dataEntries entradas " +
                    "y $dataFiles archivos, $extracted archivos en total.",
            )

            staging.listFiles().orEmpty()
                .filter { it.isFile && it.extension.equals("exe", true) && it.name.contains("online", true) }
                .forEach { if (it.delete()) say("Quitado lanzador online: ${it.name}") }

            if (hadGame) {
                if (!moveDirectory(gameDir, previous, say)) {
                    error(
                        "No pude apartar el juego anterior (${gameDir.absolutePath}). " +
                            "Si el juego está abierto, ciérralo y vuelve a intentarlo.",
                    )
                }
                movedAside = true
            }
            if (!moveDirectory(staging, gameDir, say)) {
                error("No pude mover el juego extraído a ${gameDir.absolutePath}.")
            }
            installed = true

            previous.deleteRecursively()
            if (previous.exists()) {
                say("Aviso: no pude borrar ${previous.absolutePath}; se limpiará en la próxima importación.")
            }

            if (saved.isDirectory) {
                for (folder in saveFolders) {
                    val from = File(saved, folder)
                    if (!from.isDirectory) continue
                    // Las partidas del jugador mandan sobre las que traiga el zip.
                    runCatching { from.copyRecursively(File(gameDir, "data/$folder"), overwrite = true) }
                        .onFailure {
                            keepSavesBackup = true
                            say(
                                "ERROR: no pude restaurar las partidas de $folder " +
                                    "(quedan en ${saved.absolutePath}): " +
                                    "${it.javaClass.simpleName}: ${it.message}",
                            )
                        }
                }
                say("Partidas conservadas.")
            }

            runCatching {
                metaFile.writeText(
                    listOf(
                        "installedAt=${System.currentTimeMillis()}",
                        "sizeBytes=$written",
                    ).joinToString("\n"),
                )
            }.onFailure { say("Aviso: no pude escribir game.json: ${it.javaClass.simpleName}: ${it.message}") }

            if (!keepSavesBackup) saved.deleteRecursively()
            progress(100)
            say("Juego instalado en ${gameDir.absolutePath} (${formatBytes(written)}).")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!installed) {
                if (movedAside) {
                    if (gameDir.exists() && !gameDir.deleteRecursively()) {
                        say("Aviso: no pude limpiar la instalación a medias en ${gameDir.absolutePath}.")
                    }
                    if (moveDirectory(previous, gameDir, say)) {
                        say("Juego anterior restaurado en ${gameDir.absolutePath}.")
                        saved.deleteRecursively()
                    } else {
                        keepSavesBackup = true
                        say(
                            "ERROR: no pude restaurar el juego anterior; sigue en ${previous.absolutePath} " +
                                "y tus partidas en ${saved.absolutePath}.",
                        )
                    }
                } else if (gameDir.isDirectory) {
                    // El juego anterior nunca se tocó: las partidas siguen dentro de él.
                    say("El juego anterior (${gameDir.absolutePath}) sigue intacto; no hay nada que restaurar.")
                    saved.deleteRecursively()
                } else if (saved.isDirectory) {
                    keepSavesBackup = true
                }
            }
            if (!keepSavesBackup) saved.deleteRecursively()
            throw error
        } finally {
            // Pase lo que pase, no puede quedar basura de la extracción ni un juego a medias.
            runCatching {
                if (staging.exists()) {
                    say("Limpiando la carpeta temporal ${staging.absolutePath}.")
                    staging.deleteRecursively()
                }
                if (movedAside && !installed) {
                    if (!previous.isDirectory) {
                        say("El juego anterior ya está restaurado en ${gameDir.absolutePath}.")
                    } else if (looksInstalled(gameDir)) {
                        say("El juego anterior sigue intacto; descarto ${previous.absolutePath}.")
                        previous.deleteRecursively()
                    } else {
                        say("Restauración de emergencia desde ${previous.absolutePath}.")
                        if (gameDir.exists()) gameDir.deleteRecursively()
                        if (moveDirectory(previous, gameDir, say)) {
                            say("Juego anterior restaurado en ${gameDir.absolutePath}.")
                        } else {
                            say("ERROR: no pude restaurar el juego anterior; sigue en ${previous.absolutePath}.")
                        }
                    }
                }
            }.onFailure {
                say("ERROR al limpiar/restaurar: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    /**
     * Some Reloaded ZIPs were made with a legacy code page and have no UTF-8 flag on their
     * entries. Java 17+ rejects those central directories, while CP437 reads them correctly.
     */
    private fun openZip(file: File, charset: Charset?, say: (String) -> Unit): ZipFile {
        if (charset != null) return ZipFile(file, charset)
        return try {
            ZipFile(file)
        } catch (error: ZipException) {
            if (looksEncrypted(file)) {
                throw IOException("El zip está cifrado con contraseña; descomprímelo y vuelve a comprimirlo sin clave.", error)
            }
            say("Cabecera no UTF-8 (${error.message}); reintento con Cp437.")
            try {
                ZipFile(file, Charset.forName("Cp437"))
            } catch (second: ZipException) {
                if (looksEncrypted(file)) {
                    throw IOException("El zip está cifrado con contraseña; descomprímelo y vuelve a comprimirlo sin clave.", second)
                }
                throw IOException("No pude leer el zip: ${second.javaClass.simpleName}: ${second.message}", second)
            }
        }
    }

    private data class ZipItem(
        val entry: ZipEntry,
        val relative: String,
        val directory: Boolean,
        /** Tamaño que declara el directorio central, o -1 si no es fiable. */
        val expectedSize: Long,
        /** Estimación para calcular el espacio libre cuando el tamaño no es fiable. */
        val estimateSize: Long,
    ) {
        /** El tamaño y el CRC del directorio central son fiables y se pueden exigir. */
        val declaredStrict: Boolean get() = !directory && expectedSize > 0
        val sizeKnown: Boolean get() = !directory && expectedSize >= 0
    }

    private data class ZipPlan(
        val files: List<ZipItem>,
        val totalBytes: Long,
        val root: String,
        val skipped: List<String>,
        val duplicates: List<String>,
        val unknownSizes: Int,
        val longNames: List<String>,
        val charset: Charset?,
    )

    private fun planZip(zip: File, say: (String) -> Unit): ZipPlan {
        if (!zip.isFile) error("No encuentro el zip (${zip.absolutePath}).")
        if (zip.length() == 0L) error("El archivo del juego está vacío.")
        val (entries, charset) = readEntries(zip) { say(it) }
        if (entries.isEmpty()) error("El zip está vacío.")

        val skipped = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        // LinkedHashMap: al duplicar un nombre gana la última entrada y se conserva el orden.
        val byName = LinkedHashMap<String, ZipItem>()
        var backslashes = 0
        var zeroSized = 0
        for (entry in entries) {
            val raw = entry.name
            val normalized = normalizeEntryName(raw)
            if (normalized == null) {
                skipped += raw.take(120)
                continue
            }
            if (raw.contains('\\')) backslashes++
            val directory = entry.isDirectory || raw.endsWith("/") || raw.endsWith("\\")
            if (isNoiseEntry(normalized)) {
                skipped += normalized
                continue
            }
            val entrySize = entry.size
            val compressed = entry.compressedSize
            // size == 0 con datos comprimidos significa que el empaquetador no lo declaró.
            val expected = when {
                entrySize > 0 -> entrySize
                entrySize == 0L && compressed <= 0 -> 0L
                else -> -1L
            }
            if (!directory && expected < 0) zeroSized++
            val estimate = if (expected >= 0) expected else max(compressed.coerceAtLeast(0) * 4, 16L)
            val item = ZipItem(entry, normalized, directory, expected, estimate)
            // Clave sin distinguir mayúsculas: el juego viene de Windows y su sistema de archivos
            // tampoco las distingue. Así un zip con "Data/A.txt" y "data/a.txt" no crea dos carpetas.
            if (byName.put(normalized.lowercase(Locale.ROOT), item) != null) duplicates += normalized
        }
        if (backslashes > 0) say("Normalizadas $backslashes rutas con barra invertida.")
        if (zeroSized > 0) say("$zeroSized entradas no declaran tamaño; se aceptarán al leerlas.")
        if (byName.isEmpty()) error("El zip no tiene archivos que instalar.")

        val files = byName.values.toList()
        val root = detectRoot(files, say)
        val rootPrefix = if (root.isEmpty()) "" else "$root/"
        // Las entradas duplicadas tras recortar la raíz se resuelven igual: gana la última.
        val scopedByName = LinkedHashMap<String, ZipItem>()
        for (item in files) {
            if (!item.relative.startsWith(rootPrefix, ignoreCase = true) || item.relative.length <= rootPrefix.length) {
                skipped += item.relative
                continue
            }
            val relative = item.relative.substring(rootPrefix.length)
            if (relative.isEmpty()) continue
            if (scopedByName.put(relative.lowercase(Locale.ROOT), item.copy(relative = relative)) != null) {
                duplicates += relative
            }
        }
        val scoped = scopedByName.values.toList()
        if (scoped.isEmpty()) error("El zip no tiene archivos dentro de la carpeta del juego.")

        val filePaths = scoped.filter { !it.directory }.map { it.relative.lowercase(Locale.ROOT) }.toSet()
        for (path in scoped.filter { !it.directory }.map { it.relative }) {
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (parent.lowercase(Locale.ROOT) in filePaths) {
                    error("El zip mezcla un archivo y una carpeta con el mismo nombre: «$parent».")
                }
                parent = parent.substringBeforeLast('/', "")
            }
        }

        var total = 0L
        var unknown = 0
        val longNames = mutableListOf<String>()
        for (item in scoped) {
            if (item.directory) continue
            total += item.estimateSize
            if (!item.sizeKnown) unknown++
            if (item.relative.length > 180) longNames += item.relative.take(200)
        }
        return ZipPlan(
            files = scoped,
            totalBytes = total,
            root = root,
            skipped = skipped.distinct(),
            duplicates = duplicates.distinct(),
            unknownSizes = unknown,
            longNames = longNames,
            charset = charset,
        )
    }

    /**
     * Lee el directorio central probando UTF-8 y luego las páginas de códigos que usan los ZIP
     * antiguos sin la marca UTF-8. Devuelve también el charset que funcionó, para extraer con él.
     */
    private fun readEntries(zip: File, say: (String) -> Unit): Pair<List<ZipEntry>, Charset?> {
        var firstError: Throwable? = null
        // Cp437 es el nombre de Java; Android solo expone algunas páginas de código (IBM437) y,
        // si no hay ninguna, ISO-8859-1 al menos conserva los bytes altos sin romper la lectura.
        for (charsetName in listOf(null, "Cp437", "IBM437", "Cp850", "IBM850", "windows-1252", "ISO-8859-1")) {
            val charset = charsetName?.let { name ->
                try {
                    Charset.forName(name)
                } catch (error: Throwable) {
                    say("Charset $name no disponible en este dispositivo; probando el siguiente.")
                    null
                }
            } ?: if (charsetName == null) null else continue
            try {
                val entries = if (charset == null) {
                    ZipFile(zip).use { it.entries().toList() }
                } else {
                    ZipFile(zip, charset).use { it.entries().toList() }
                }
                if (charset != null) {
                    say("Cabecera no UTF-8 (${firstError?.message}); nombres leídos como $charset.")
                }
                return entries to charset
            } catch (error: ZipException) {
                if (firstError == null) firstError = error
            }
        }
        if (looksEncrypted(zip)) {
            error("El zip está cifrado con contraseña; descomprímelo y vuelve a comprimirlo sin clave.")
        }
        error(
            "No pude leer el zip: ${firstError?.javaClass?.simpleName}: ${firstError?.message}. " +
                "Puede estar dañado o incompleto; vuelve a descargarlo.",
        )
    }

    private fun normalizeEntryName(raw: String): String? {
        var name = raw
        if (name.startsWith("\uFEFF")) name = name.substring(1)
        name = name.replace('\\', '/')
        name = name.replace(Regex("/{2,}"), "/")
        while (name.startsWith("./")) name = name.substring(2)
        name = name.trimStart('/')
        name = name.replace(Regex("^[A-Za-z]:/"), "")
        while (name.endsWith("/")) name = name.dropLast(1)
        if (name.isEmpty()) return null
        if ('\u0000' in name) return null
        val segments = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) return null
        if (segments.any { it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun isNoiseEntry(path: String): Boolean {
        val segments = path.split('/')
        if (segments.any { it.equals("__MACOSX", ignoreCase = true) }) return true
        val last = segments.lastOrNull() ?: return true
        if (last.startsWith("._")) return true
        return last.equals(".DS_Store", ignoreCase = true) ||
            last.equals("Thumbs.db", ignoreCase = true) ||
            last.equals("desktop.ini", ignoreCase = true)
    }

    private fun detectRoot(files: List<ZipItem>, say: (String) -> Unit): String {
        val filePaths = files.filter { !it.directory }.map { it.relative }
        val exes = filePaths.filter { it.substringAfterLast('/').endsWith(".exe", true) }
        if (exes.isEmpty()) {
            error("El zip no contiene ningún .exe. Archivos: ${filePaths.take(10).joinToString(", ")}")
        }
        val exe = exes.sortedWith(
            compareBy({ it.contains("online", ignoreCase = true) }, { it.count { c -> c == '/' } }, { it }),
        ).first()
        if (exes.size > 1) say("Varios .exe (${exes.take(5).joinToString(", ")}); uso «$exe».")
        val exeParent = exe.substringBeforeLast('/', "")

        val allDirs = linkedSetOf<String>()
        fun addParents(path: String) {
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                allDirs += parent
                parent = parent.substringBeforeLast('/', "")
            }
        }
        filePaths.forEach(::addParents)
        files.filter { it.directory }.forEach { addParents(it.relative) }
        val dataDirs = allDirs
            .filter { it.substringAfterLast('/').equals("data", ignoreCase = true) }
            .sortedWith(compareBy({ it.count { c -> c == '/' } }, { it }))
        if (dataDirs.isEmpty()) {
            error("El zip no contiene la carpeta data/. Archivos: ${filePaths.take(10).joinToString(", ")}")
        }

        val sameLevel = dataDirs.firstOrNull {
            it.substringBeforeLast('/', "").equals(exeParent, ignoreCase = true)
        }
        if (sameLevel != null) return exeParent

        val dataDir = dataDirs.first()
        val root = commonAncestor(exeParent, dataDir.substringBeforeLast('/', ""))
        say(
            "El .exe («$exe») y data/ («$dataDir») no están al mismo nivel; " +
                "raíz común «${root.ifEmpty { "(raíz)" }}».",
        )
        return root
    }

    private fun commonAncestor(a: String, b: String): String {
        val left = a.split('/').filter { it.isNotEmpty() }
        val right = b.split('/').filter { it.isNotEmpty() }
        var index = 0
        while (index < left.size && index < right.size && left[index].equals(right[index], ignoreCase = true)) {
            index++
        }
        return left.take(index).joinToString("/")
    }

    /** Deja el .exe y data/ en la raíz de la instalación aunque el zip los anide. */
    private fun normalizeStaging(staging: File, say: (String) -> Unit) {
        var exe = findExe(staging)
        if (exe == null) {
            val nested = deepFindExe(staging, 0)
            if (nested != null) {
                val from = nested.parentFile
                if (from != null && from != staging) {
                    say("El .exe está en ${from.relativeTo(staging).invariantSeparatorsPath}; moviéndolo a la raíz.")
                    from.listFiles().orEmpty()
                        .filter { !it.name.equals("data", ignoreCase = true) }
                        .forEach { child ->
                            val target = File(staging, child.name)
                            if (target.exists()) return@forEach
                            if (child.renameTo(target)) return@forEach
                            runCatching {
                                if (child.isDirectory) child.copyRecursively(target, overwrite = false)
                                else child.copyTo(target, overwrite = false)
                            }.onSuccess {
                                if (!child.deleteRecursively()) {
                                    say("Aviso: copiado ${child.name}, pero no pude borrar el original.")
                                }
                            }.onFailure { say("Aviso: no pude mover ${child.name}: ${it.message}") }
                        }
                    exe = findExe(staging)
                }
            }
        }
        val data = File(staging, "data")
        if (!data.isDirectory) {
            val nestedData = deepFindDir(staging, "data", 0)
            if (nestedData != null && nestedData != staging) {
                say("data/ está en ${nestedData.relativeTo(staging).invariantSeparatorsPath}; moviéndola a la raíz.")
                if (data.exists() && !data.deleteRecursively()) {
                    say("Aviso: no pude quitar ${data.absolutePath} antes de mover data/.")
                }
                if (!nestedData.renameTo(data)) {
                    runCatching { nestedData.copyRecursively(data, overwrite = true) }
                        .onSuccess { nestedData.deleteRecursively() }
                        .onFailure { say("Aviso: no pude mover data/: ${it.message}") }
                }
            }
        }
        // El motor y status() buscan exactamente "data"; un "DATA" del zip se renombra.
        staging.listFiles().orEmpty()
            .firstOrNull { it.isDirectory && it.name != "data" && it.name.equals("data", ignoreCase = true) }
            ?.let { folder ->
                val target = File(staging, "data")
                if (target.exists()) {
                    say("Aviso: hay más de una carpeta data/ en el zip; uso ${folder.name}.")
                } else if (folder.renameTo(target)) {
                    say("Carpeta «${folder.name}» renombrada a data/ (el juego la busca en minúsculas).")
                } else {
                    say("Aviso: la carpeta se llama «${folder.name}» y no pude renombrarla a data/.")
                }
            }
    }

    private fun deepFindExe(dir: File, depth: Int): File? {
        if (depth > 3) return null
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("exe", true) }
            .sortedWith(compareBy({ it.name.contains("online", ignoreCase = true) }, { it.name }))
            .firstOrNull()
            ?.let { return it }
        dir.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.equals("data", ignoreCase = true) && !it.name.startsWith(".") }
            .sortedBy { it.name }
            .forEach { child -> deepFindExe(child, depth + 1)?.let { return it } }
        return null
    }

    private fun looksInstalled(dir: File): Boolean =
        dir.isDirectory && findExe(dir) != null && File(dir, "data").isDirectory

    private fun deepFindDir(dir: File, name: String, depth: Int): File? {
        if (depth > 3) return null
        dir.listFiles().orEmpty().filter { it.isDirectory }.forEach { child ->
            if (child.name.equals(name, ignoreCase = true)) return child
            deepFindDir(child, name, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Mueve una carpeta entera; si `renameTo` falla (filesystem distinto, archivos abiertos en
     * Android) prueba `Files.move` y, como último recurso, copia y verifica antes de borrar.
     */
    private fun moveDirectory(from: File, to: File, say: (String) -> Unit): Boolean {
        if (!from.exists()) return false
        to.deleteRecursively()
        if (from.renameTo(to)) return true
        runCatching {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return true
        }.onFailure { say("rename/ATOMIC_MOVE falló (${it.javaClass.simpleName}: ${it.message}); probando Files.move.") }
        runCatching {
            Files.move(from.toPath(), to.toPath())
            return true
        }.onFailure { say("Files.move falló (${it.javaClass.simpleName}: ${it.message}); copiando.") }
        return try {
            from.copyRecursively(to, overwrite = false)
            val expected = fileStats(from)
            val actual = fileStats(to)
            if (expected != actual) {
                say("Copia incompleta: $actual de $expected; no borro el original.")
                to.deleteRecursively()
                false
            } else {
                val deleted = from.deleteRecursively()
                if (!deleted) say("Aviso: copiado, pero no pude borrar ${from.absolutePath}.")
                true
            }
        } catch (error: Throwable) {
            say("No pude copiar ${from.absolutePath} a ${to.absolutePath}: ${error.javaClass.simpleName}: ${error.message}")
            to.deleteRecursively()
            false
        }
    }

    private data class FileStats(val files: Int, val bytes: Long)

    private fun fileStats(dir: File): FileStats {
        var files = 0
        var bytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                files++
                bytes += file.length()
            }
        }
        return FileStats(files, bytes)
    }

    private fun countFiles(dir: File): Int = dir.walkTopDown().count { it.isFile }

    /** Detecta el bit de cifrado del directorio central sin descomprimir nada. */
    private fun looksEncrypted(file: File): Boolean = runCatching {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length < 22) return@runCatching false
            val tailSize = minOf(length, 70_000L).toInt()
            val tail = ByteArray(tailSize)
            raf.seek(length - tailSize)
            raf.readFully(tail)
            var eocd = -1
            for (index in tail.size - 22 downTo 0) {
                if (le32(tail, index) == 0x06054b50L) {
                    eocd = index
                    break
                }
            }
            if (eocd < 0) return@runCatching false
            val count = le16(tail, eocd + 10)
            var offset = le32(tail, eocd + 16)
            if (count == 0xFFFF || offset == 0xFFFFFFFFL) return@runCatching false
            val header = ByteArray(46)
            repeat(count) {
                raf.seek(offset)
                raf.readFully(header)
                if (le32(header, 0) != 0x02014b50L) return@runCatching false
                if (le16(header, 8) and 1 != 0) return@runCatching true
                offset += 46L + le16(header, 28) + le16(header, 30) + le16(header, 32)
            }
            false
        }
    }.getOrDefault(false)

    private fun le16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)

    private fun freeBytes(): Long = runCatching { root.usableSpace }.getOrDefault(0L)

    private fun isAndroid(): Boolean =
        System.getProperty("java.vm.name").orEmpty().contains("dalvik", ignoreCase = true) ||
            System.getProperty("java.runtime.name").orEmpty().contains("android", ignoreCase = true)

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
