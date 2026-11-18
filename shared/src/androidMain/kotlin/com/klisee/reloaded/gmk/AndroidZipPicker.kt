package com.klisee.reloaded.gmk

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Copia el ZIP elegido en el selector de Android a la caché interna antes de importarlo.
 *
 * El selector entrega una URI, no un archivo: algunos proveedores (Drive, Descargas, MTP) solo
 * permiten leer una vez y pueden tardar o cortarse a mitad. Por eso la copia se hace a un
 * `.part`, se comprueba el espacio libre antes de empezar y solo se publica el archivo final
 * cuando está completo.
 */
object AndroidZipPicker {

    private const val CACHE_NAME = "import.zip"
    private const val MARGIN_BYTES = 8L * 1024 * 1024
    private const val STALL_MILLIS = 5L * 60 * 1000

    fun copyToCache(context: Context, uri: Uri, log: (String) -> Unit): Result<File> = runCatching {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "zip"
        val declared = querySize(resolver, uri)
        val needed = (declared ?: 0L) + MARGIN_BYTES

        // La caché puede ser pequeña en móviles con poco espacio; si no cabe, se usa
        // filesDir (también interno y con más cuota) antes de rendirse.
        val cache = context.cacheDir.apply { mkdirs() }
        val work = File(context.filesDir, "import-tmp").apply { mkdirs() }
        // Restos de una importación anterior en cualquiera de los dos destinos.
        listOf(cache, work).forEach { dir ->
            File(dir, CACHE_NAME).delete()
            File(dir, "$CACHE_NAME.part").delete()
        }
        val cacheFree = runCatching { cache.usableSpace }.getOrDefault(0L)
        val workFree = runCatching { work.usableSpace }.getOrDefault(0L)
        val dir = when {
            cache.isDirectory && (declared == null || cacheFree <= 0 || cacheFree >= needed) -> cache
            work.isDirectory && (declared == null || workFree <= 0 || workFree >= needed) -> work
            else -> cache
        }
        val target = File(dir, CACHE_NAME)
        val part = File(dir, "$CACHE_NAME.part")
        val free = runCatching { dir.usableSpace }.getOrDefault(0L)
        log(
            "Selector: «$displayName» uri=$uri tamaño=${declared?.let(::formatBytes) ?: "desconocido"} " +
                "destino=${dir.absolutePath} libre=${if (free > 0) formatBytes(free) else "desconocido"} " +
                "(caché libre=${if (cacheFree > 0) formatBytes(cacheFree) else "desconocido"})",
        )
        if (declared != null && declared > 0 && free > 0 && free < needed) {
            error(
                "No hay espacio suficiente para copiar el zip: ocupa ${formatBytes(declared)} y quedan " +
                    "${formatBytes(free)} libres en el almacenamiento interno. " +
                    "Libera al menos ${formatBytes(needed - free)} y vuelve a intentarlo.",
            )
        }

        var copied = 0L
        val lastActivity = AtomicLong(System.currentTimeMillis())
        val stalled = AtomicBoolean(false)
        val stream = java.util.concurrent.atomic.AtomicReference<InputStream?>(null)
        // Un proveedor de documentos puede quedarse colgado sin devolver error (Drive, MTP).
        // Sin este vigilante la importación se quedaría para siempre "copiando".
        val watchdog = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(5_000)
                } catch (interrupted: InterruptedException) {
                    return@Thread
                }
                if (System.currentTimeMillis() - lastActivity.get() > STALL_MILLIS) {
                    stalled.set(true)
                    runCatching { stream.get()?.close() }
                    return@Thread
                }
            }
        }.apply {
            name = "import-zip-watchdog"
            isDaemon = true
            start()
        }
        try {
            val input = resolver.openInputStream(uri)
                ?: error("No pude abrir el archivo elegido (el proveedor no devolvió datos).")
            stream.set(input)
            input.use { source ->
                FileOutputStream(part).use { sink ->
                    val buffer = ByteArray(128 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val read = try {
                            source.read(buffer)
                        } catch (error: Throwable) {
                            if (stalled.get()) {
                                throw IOException(
                                    "El proveedor del archivo dejó de responder; copiados ${formatBytes(copied)}. " +
                                        "Copia el zip a Descargas y vuelve a elegirlo desde ahí.",
                                    error,
                                )
                            }
                            throw IOException(
                                "Se cortó la lectura del archivo a los ${formatBytes(copied)}: " +
                                    "${error.javaClass.simpleName}: ${error.message}",
                                error,
                            )
                        }
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                        lastActivity.set(System.currentTimeMillis())
                        if (declared != null && declared > 0 && copied > declared * 2 + 1_000_000) {
                            throw IOException(
                                "El archivo no parece un zip: he leído más del doble de los $declared bytes " +
                                    "que declara. ¿Elegiste el archivo correcto?",
                            )
                        }
                        if (copied - lastReport >= 32L * 1024 * 1024) {
                            lastReport = copied
                            log("Copiando zip: ${formatBytes(copied)}…")
                        }
                    }
                    sink.flush()
                    sink.fd.sync()
                }
            }
        } catch (error: Throwable) {
            part.delete()
            throw when {
                error is IOException && error.message?.contains("No space", true) == true ->
                    IOException("No queda espacio en el almacenamiento interno al copiar el zip.", error)
                else -> error
            }
        } finally {
            stream.set(null)
            watchdog.interrupt()
        }

        if (!part.isFile) {
            part.delete()
            error("La copia del zip no llegó a escribirse.")
        }
        if (part.length() == 0L) {
            part.delete()
            error("El archivo elegido está vacío (0 bytes).")
        }
        if (declared != null && declared > 0 && part.length() != declared) {
            log("Aviso: el proveedor declaraba ${formatBytes(declared)} pero se copiaron ${formatBytes(part.length())}.")
        }
        if (!looksLikeZip(part)) {
            part.delete()
            error("El archivo elegido no es un ZIP válido. Elige el zip del juego, no una carpeta ni otro archivo.")
        }
        if (!part.renameTo(target)) {
            // Algunos gestores de archivos dejan el .part abierto: se reintenta tras soltarlo.
            target.delete()
            if (!part.renameTo(target)) {
                part.delete()
                error("No pude preparar el zip temporal en ${target.absolutePath}.")
            }
        }
        log("Zip copiado a ${target.absolutePath} (${formatBytes(target.length())}).")
        target
    }.onFailure { error ->
        log(
            "No se pudo preparar el zip: ${error.javaClass.simpleName}: ${error.message}",
        )
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index).takeIf { it > 0 } else null
            } else {
                null
            }
        }
    }.getOrNull()

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    /** PK\x03\x04, PK\x05\x06 (vacío) o PK\x07\x08; también acepta un SFX con cabecera delante. */
    private fun looksLikeZip(file: File): Boolean = runCatching {
        val head = ByteArray(64 * 1024)
        val read = file.inputStream().use { it.read(head) }
        if (read < 4) return@runCatching false
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) return@runCatching true
        for (index in 0..read - 4) {
            if (head[index] == 'P'.code.toByte() && head[index + 1] == 'K'.code.toByte() &&
                head[index + 2] == 3.toByte() && head[index + 3] == 4.toByte()
            ) {
                return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
