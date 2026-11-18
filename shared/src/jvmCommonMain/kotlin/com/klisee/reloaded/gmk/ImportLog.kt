package com.klisee.reloaded.gmk

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Escribe las líneas de la importación en `app.log` con marca de tiempo.
 *
 * La importación de Android no tiene consola, así que este archivo es lo único que el
 * jugador puede enviar cuando algo falla. Se rota una vez al pasar de 2 MB para que un
 * zip dañado que se reintenta muchas veces no llene el almacenamiento.
 */
internal class ImportLog(private val file: File) {

    fun line(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val text = "$stamp [import] $message\n"
        synchronized(LOCK) {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.isFile && file.length() > MAX_BYTES) {
                    val rotated = File(file.parentFile, file.name + ".1")
                    rotated.delete()
                    file.renameTo(rotated)
                }
                FileOutputStream(file, true).use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    fun stackTrace(error: Throwable) {
        line("traza:\n" + error.stackTraceToString().trimEnd())
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        private val LOCK = Any()
    }
}
