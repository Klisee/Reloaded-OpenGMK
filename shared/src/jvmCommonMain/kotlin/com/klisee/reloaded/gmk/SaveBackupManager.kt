package com.klisee.reloaded.gmk

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveBackupManager(private val root: File) {

    data class BackupInfo(
        val id: String,
        val date: Long,
        val sizeBytes: Long,
        val reason: String,
        val file: File,
    )

    private val onlineDir: File get() = File(root, "backups/online")
    private val quarantineDir: File get() = File(root, "backups/quarantine")
    private val stagingDir: File get() = File(root, "backups/staging")

    fun create(reason: String): File? {
        val data = File(root, "game/data")
        val folders = SAVE_FOLDERS.map { File(data, it) }.filter { it.isDirectory }
        if (folders.isEmpty()) return null
        if (!onlineDir.isDirectory && !onlineDir.mkdirs()) return null

        val now = System.currentTimeMillis()
        val destination = uniqueFile(onlineDir, "online-${stamp(now)}")
        val temp = File(onlineDir, "${destination.name}.tmp")
        temp.delete()
        return try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                val manifest = buildString {
                    appendLine("format=1")
                    appendLine("created=$now")
                    appendLine("reason=${oneLine(reason)}")
                    appendLine("folders=${folders.joinToString(",") { it.name }}")
                }
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                folders.forEach { folder ->
                    folder.walkTopDown().forEach { source ->
                        if (source.isDirectory && source.name == "preonline") return@forEach
                        if (source.isFile && source.name.endsWith(".online")) return@forEach
                        val relative = if (source == folder) {
                            folder.name + "/"
                        } else {
                            folder.name + "/" +
                                source.relativeTo(folder).invariantSeparatorsPath +
                                if (source.isDirectory) "/" else ""
                        }
                        zip.putNextEntry(ZipEntry(relative))
                        if (source.isFile) {
                            source.inputStream().buffered().use { it.copyTo(zip) }
                        }
                        zip.closeEntry()
                    }
                }
            }
            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            destination.takeIf { it.isFile }
        } catch (_: Exception) {
            temp.delete()
            null
        }
    }

    fun list(): List<BackupInfo> =
        onlineDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("zip", true) }
            .mapNotNull(::info)
            .sortedByDescending { it.date }

    fun restore(backup: File, target: File): Boolean {
        if (!backup.isFile) return false
        val now = System.currentTimeMillis()
        val staging = uniqueDir(stagingDir, "restore-${stamp(now)}")
        return try {
            if (!staging.mkdirs()) return false
            extract(backup, staging)
            if (SAVE_FOLDERS.none { File(staging, it).isDirectory }) return false
            if (!target.isDirectory && !target.mkdirs()) return false

            val quarantine = uniqueDir(quarantineDir, "restore-${stamp(now)}")
            if (!quarantine.mkdirs()) return false

            val present = SAVE_FOLDERS.filter { File(staging, it).isDirectory }
            if (present.isEmpty()) return false
            val moved = mutableListOf<String>()
            val installed = mutableListOf<String>()
            try {
                present.forEach { name ->
                    val current = File(target, name)
                    if (!current.exists()) return@forEach
                    if (!move(current, File(quarantine, name))) error("No pude apartar $name.")
                    moved += name
                }
                present.forEach { name ->
                    val source = File(staging, name)
                    val destination = File(target, name)
                    destination.deleteRecursively()
                    if (!move(source, destination)) error("No pude restaurar $name.")
                    installed += name
                }
            } catch (_: Exception) {
                installed.forEach { File(target, it).deleteRecursively() }
                moved.forEach { name ->
                    val saved = File(quarantine, name)
                    val destination = File(target, name)
                    destination.deleteRecursively()
                    if (saved.exists()) move(saved, destination)
                }
                return false
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            staging.deleteRecursively()
            stagingDir.delete()
        }
    }

    fun prune(max: Int = 5, maxAgeDays: Int = 30) {
        val cutoff = if (maxAgeDays > 0) {
            System.currentTimeMillis() - maxAgeDays.toLong() * 24L * 60L * 60L * 1000L
        } else {
            Long.MIN_VALUE
        }
        list().forEachIndexed { index, info ->
            val tooMany = max >= 0 && index >= max
            val tooOld = maxAgeDays > 0 && info.date < cutoff
            if (tooMany || tooOld) info.file.delete()
        }
    }

    private fun info(file: File): BackupInfo? {
        val manifest = runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(MANIFEST_ENTRY) ?: return@use emptyMap()
                parseManifest(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
        }.getOrDefault(emptyMap())
        val date = manifest["created"]?.toLongOrNull() ?: file.lastModified()
        return BackupInfo(
            id = file.nameWithoutExtension,
            date = date,
            sizeBytes = file.length(),
            reason = manifest["reason"].orEmpty(),
            file = file,
        )
    }

    private fun extract(backup: File, staging: File) {
        val stagingPath = staging.canonicalFile.path + File.separator
        ZipInputStream(backup.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name == MANIFEST_ENTRY) {
                    input.closeEntry()
                    entry = input.nextEntry
                    continue
                }
                val parts = name.split('/').filter { it.isNotEmpty() }
                if (
                    name.startsWith("/") ||
                    parts.isEmpty() ||
                    parts.first() !in SAVE_FOLDERS ||
                    parts.any { it == "." || it == ".." || it.contains('\u0000') }
                ) {
                    error("El backup trae una ruta no permitida: $name")
                }
                val destination = File(staging, parts.joinToString("/"))
                if (!destination.canonicalFile.path.startsWith(stagingPath)) {
                    error("El backup trae una ruta fuera del destino: $name")
                }
                if (entry.isDirectory || name.endsWith("/")) {
                    if (!destination.isDirectory && !destination.mkdirs()) {
                        error("No pude crear ${destination.name}.")
                    }
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }

    private fun move(source: File, destination: File): Boolean {
        destination.parentFile?.mkdirs()
        if (destination.exists() && !destination.deleteRecursively()) return false
        if (source.renameTo(destination)) return true
        return runCatching {
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = true)
            } else {
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
            }
            source.deleteRecursively()
        }.getOrDefault(false)
    }

    private fun uniqueFile(dir: File, base: String): File {
        var file = File(dir, "$base.zip")
        var index = 2
        while (file.exists()) {
            file = File(dir, "$base-$index.zip")
            index++
        }
        return file
    }

    private fun uniqueDir(parent: File, base: String): File {
        parent.mkdirs()
        var dir = File(parent, base)
        var index = 2
        while (dir.exists()) {
            dir = File(parent, "$base-$index")
            index++
        }
        return dir
    }

    private fun parseManifest(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }.toMap()

    private fun oneLine(value: String): String = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(millis))

    private companion object {
        val SAVE_FOLDERS = listOf("partidas", "equipos", "char_custom")
        const val MANIFEST_ENTRY = "manifest.txt"
    }
}
