package com.klisee.reloaded.gmk

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportZipTest {

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "reloaded-import-$name").apply {
            deleteRecursively()
            mkdirs()
        }

    private fun buildZip(
        file: File,
        entries: List<Pair<String, ByteArray?>>,
        charset: Charset? = null,
    ) {
        val stream = ZipOutputStream(file.outputStream(), charset ?: Charsets.UTF_8)
        stream.use { zip ->
            for ((name, data) in entries) {
                if (data == null) {
                    val entry = ZipEntry(name)
                    if (charset != null) entry.setMethod(ZipEntry.DEFLATED)
                    zip.putNextEntry(entry)
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun gameEntries(root: String = "") = listOf(
        "$root" + "Reloaded.exe" to "MZ fake exe".toByteArray(),
        "$root" + "data/" to null,
        "$root" + "data/config.ini" to "config".toByteArray(),
        "$root" + "data/partidas/reloaded1.sav" to "save".toByteArray(),
        "$root" + "data/cache/ui/pkmn_1.png" to ByteArray(64) { it.toByte() },
        "$root" + "data/text/config.ini" to "texto".toByteArray(),
        "$root" + "data/audio/musica.ogg" to ByteArray(128) { 7 },
    )

    private fun import(
        repo: GameRepository,
        zip: File,
        logs: MutableList<String>,
    ): Result<Unit> = runBlocking {
        repo.importZip(zip, onProgress = {}, onLog = { logs += it })
    }

    private fun corruptStoredEntry(zip: File, entryName: String) {
        val bytes = zip.readBytes()
        val marker = entryName.toByteArray()
        var at = -1
        for (index in 0..bytes.size - marker.size) {
            var match = true
            for (offset in marker.indices) {
                if (bytes[index + offset] != marker[offset]) {
                    match = false
                    break
                }
            }
            if (match) {
                at = index
                break
            }
        }
        assertTrue(at > 0, "no encontré la entrada $entryName")
        val local = at - 30
        assertTrue(local > 0, "cabecera local no encontrada")
        val nameLength = (bytes[local + 26].toInt() and 0xFF) or ((bytes[local + 27].toInt() and 0xFF) shl 8)
        val extraLength = (bytes[local + 28].toInt() and 0xFF) or ((bytes[local + 29].toInt() and 0xFF) shl 8)
        val dataStart = local + 30 + nameLength + extraLength
        assertTrue(dataStart < bytes.size, "los datos de la entrada no están en el archivo")
        bytes[dataStart] = (bytes[dataStart].toInt() xor 0x7F).toByte()
        zip.writeBytes(bytes)
    }

    private fun writeStoredGameZip(zip: File) {
        ZipOutputStream(zip.outputStream()).use { out ->
            for ((name, data) in gameEntries()) {
                if (data == null) {
                    out.putNextEntry(ZipEntry(name))
                    out.closeEntry()
                    continue
                }
                val entry = ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.compressedSize = data.size.toLong()
                entry.crc = CRC32().apply { update(data) }.value
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }
        }
    }

    private fun zeroDeclaredSize(zip: File, entryName: String) {
        val bytes = zip.readBytes()
        val name = entryName.toByteArray()
        var at = -1
        for (index in 0..bytes.size - 46 - name.size) {
            if (bytes[index] != 'P'.code.toByte() || bytes[index + 1] != 'K'.code.toByte()) continue
            if (bytes[index + 2] != 1.toByte() || bytes[index + 3] != 2.toByte()) continue
            var match = true
            for (offset in name.indices) {
                if (bytes[index + 46 + offset] != name[offset]) {
                    match = false
                    break
                }
            }
            if (match) {
                at = index
                break
            }
        }
        assertTrue(at > 0, "no encontré la entrada central de $entryName")
        val sizeField = at + 24
        for (offset in 0 until 4) bytes[sizeField + offset] = 0
        zip.writeBytes(bytes)
    }


    /** Escribe un zip "a mano" que admite nombres repetidos (ZipOutputStream no lo permite). */
    private fun buildRawZip(file: File, entries: List<Pair<String, ByteArray>>) {
        fun le16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
        fun le32(value: Long) = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
        val out = java.io.ByteArrayOutputStream()
        data class Central(
            val name: ByteArray,
            val crc: Long,
            val size: Long,
            val offset: Long,
        )
        val central = mutableListOf<Central>()
        for ((name, data) in entries) {
            val nameBytes = name.toByteArray()
            val crc = CRC32().apply { update(data) }.value
            val offset = out.size().toLong()
            out.write(le32(0x04034b50L))
            out.write(le16(20))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0x21))
            out.write(le32(crc))
            out.write(le32(data.size.toLong()))
            out.write(le32(data.size.toLong()))
            out.write(le16(nameBytes.size))
            out.write(le16(0))
            out.write(nameBytes)
            out.write(data)
            central += Central(nameBytes, crc, data.size.toLong(), offset)
        }
        val centralStart = out.size().toLong()
        for (item in central) {
            out.write(le32(0x02014b50L))
            out.write(le16(20))
            out.write(le16(20))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0x21))
            out.write(le32(item.crc))
            out.write(le32(item.size))
            out.write(le32(item.size))
            out.write(le16(item.name.size))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le16(0))
            out.write(le32(0))
            out.write(le32(item.offset))
            out.write(item.name)
        }
        val centralSize = out.size().toLong() - centralStart
        out.write(le32(0x06054b50L))
        out.write(le16(0))
        out.write(le16(0))
        out.write(le16(central.size))
        out.write(le16(central.size))
        out.write(le32(centralSize))
        out.write(le32(centralStart))
        out.write(le16(0))
        file.writeBytes(out.toByteArray())
    }

    /** Marca una entrada del directorio central como cifrada, sin cifrar los datos. */
    private fun markEncrypted(zip: File, entryName: String) {
        val bytes = zip.readBytes()
        val name = entryName.toByteArray()
        var at = -1
        for (index in 0..bytes.size - 46 - name.size) {
            if (bytes[index] != 'P'.code.toByte() || bytes[index + 1] != 'K'.code.toByte()) continue
            if (bytes[index + 2] != 1.toByte() || bytes[index + 3] != 2.toByte()) continue
            var match = true
            for (offset in name.indices) {
                if (bytes[index + 46 + offset] != name[offset]) {
                    match = false
                    break
                }
            }
            if (match) {
                at = index
                break
            }
        }
        assertTrue(at > 0, "no encontré la entrada central de $entryName")
        val flags = at + 8
        bytes[flags] = (bytes[flags].toInt() or 1).toByte()
        zip.writeBytes(bytes)
    }

    @Test
    fun un_zip_cifrado_avisa_con_un_mensaje_claro() {
        val root = tempDir("cifrado")
        val zip = File(root, "juego.zip")
        buildZip(zip, gameEntries())
        markEncrypted(zip, "data/config.ini")
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("cifrado"),
            "${result.exceptionOrNull()?.message}",
        )
        assertFalse(repo.gameDir.exists())
        assertTrue(logs.any { it.contains("cifrado") }, "$logs")
    }

    @Test
    fun instala_un_zip_plano() {
        val root = tempDir("plano")
        val zip = File(root, "juego.zip")
        buildZip(zip, gameEntries())
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isSuccess, "debía instalar: ${result.exceptionOrNull()}")
        assertTrue(File(repo.gameDir, "Reloaded.exe").isFile)
        assertTrue(File(repo.gameDir, "data/config.ini").isFile)
        assertEquals("config", File(repo.gameDir, "data/config.ini").readText())
        assertFalse(File(File(root, "home"), "game.staging").exists())
        assertTrue(logs.any { it.contains("Plan:") }, "faltan logs del plan")
        assertTrue(repo.logFile.isFile, "el log debe persistirse en app.log")
        val log = repo.logFile.readText()
        assertTrue(log.contains("[import]"), "app.log debe llevar marca de importación: $log")
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(log), "app.log debe llevar fecha")
    }

    @Test
    fun instala_un_zip_con_una_carpeta_raiz() {
        val root = tempDir("raiz")
        val zip = File(root, "juego.zip")
        buildZip(zip, gameEntries("Pokemon Reloaded/"))
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertTrue(File(repo.gameDir, "Reloaded.exe").isFile)
        assertTrue(File(repo.gameDir, "data/config.ini").isFile)
        assertFalse(File(repo.gameDir, "Pokemon Reloaded").exists())
    }

    @Test
    fun acepta_acentos_y_quita_el_bom_del_nombre() {
        val root = tempDir("acentos")
        val zip = File(root, "juego.zip")
        val entries = gameEntries("Juego/") + listOf(
            "Juego/data/ñandú/canción.txt" to "acentuado".toByteArray(),
            "\uFEFFJuego/data/leído.txt" to "bom".toByteArray(),
        )
        buildZip(zip, entries)
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("acentuado", File(repo.gameDir, "data/ñandú/canción.txt").readText())
        assertEquals("bom", File(repo.gameDir, "data/leído.txt").readText())
    }

    @Test
    fun acepta_un_zip_cp437_sin_marca_utf8() {
        val root = tempDir("cp437")
        val zip = File(root, "juego.zip")
        val entries = listOf(
            "Pokémon Reloaded/Reloaded.exe" to "exe".toByteArray(),
            "Pokémon Reloaded/data/config.ini" to "config".toByteArray(),
            "Pokémon Reloaded/data/ñ/acento.txt" to "ok".toByteArray(),
        )
        buildZip(zip, entries, Charset.forName("Cp437"))
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertTrue(File(repo.gameDir, "Reloaded.exe").isFile)
        assertTrue(
            logs.any { it.contains("Cp437") || it.contains("IBM437") },
            "debe avisar del charset: $logs",
        )
    }

    @Test
    fun normaliza_rutas_con_barra_invertida() {
        val root = tempDir("backslash")
        val zip = File(root, "juego.zip")
        val entries = listOf(
            "Juego\\Reloaded.exe" to "exe".toByteArray(),
            "Juego\\data\\config.ini" to "config".toByteArray(),
            "Juego\\data\\sub\\archivo.txt" to "sub".toByteArray(),
        )
        buildZip(zip, entries)
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("sub", File(repo.gameDir, "data/sub/archivo.txt").readText())
    }

    @Test
    fun ignora_macosx_y_ds_store() {
        val root = tempDir("macosx")
        val zip = File(root, "juego.zip")
        val entries = gameEntries("Juego/") + listOf(
            "__MACOSX/" to null,
            "__MACOSX/Juego/._Reloaded.exe" to "basura".toByteArray(),
            "Juego/__MACOSX/._data" to "basura".toByteArray(),
            "Juego/data/.DS_Store" to "basura".toByteArray(),
            "Juego/.DS_Store" to "basura".toByteArray(),
        )
        buildZip(zip, entries)
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertFalse(File(repo.gameDir, ".DS_Store").exists())
        assertFalse(File(repo.gameDir, "__MACOSX").exists())
        assertFalse(File(repo.gameDir, "data/.DS_Store").exists())
        assertTrue(logs.any { it.contains("Omitidas") }, "debe registrar lo omitido")
    }

    @Test
    fun una_entrada_corrupta_falla_y_no_deja_instalacion() {
        val root = tempDir("corrupta")
        val zip = File(root, "juego.zip")
        writeStoredGameZip(zip)
        corruptStoredEntry(zip, "data/config.ini")
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isFailure, "un zip corrupto no debe instalar")
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("data/config.ini"), "debe nombrar el archivo: $message")
        assertFalse(repo.gameDir.exists(), "no debe quedar un juego a medias")
        assertFalse(File(File(root, "home"), "game.staging").exists())
        assertTrue(logs.any { it.contains("IMPORTACIÓN FALLIDA") })
        assertTrue(repo.logFile.readText().contains("IMPORTACIÓN FALLIDA"))
    }

    @Test
    fun un_zip_truncado_falla_con_mensaje_claro() {
        val root = tempDir("truncado")
        val good = File(root, "bueno.zip")
        buildZip(good, gameEntries())
        val zip = File(root, "juego.zip")
        zip.writeBytes(good.readBytes().copyOf(good.length().toInt() / 2))
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("No pude leer el zip"))
    }

    @Test
    fun recupera_el_juego_anterior_si_el_nuevo_falla() {
        val root = tempDir("rollback")
        val home = File(root, "home")
        val repo = GameRepository(home)
        val good = File(root, "bueno.zip")
        buildZip(good, gameEntries())
        assertTrue(import(repo, good, mutableListOf()).isSuccess)
        val marker = File(repo.gameDir, "data/config.ini")
        assertEquals("config", marker.readText())
        val oldExeSize = File(repo.gameDir, "Reloaded.exe").length()

        val bad = File(root, "malo.zip")
        writeStoredGameZip(bad)
        corruptStoredEntry(bad, "data/config.ini")
        val logs = mutableListOf<String>()
        val result = import(repo, bad, logs)
        assertTrue(result.isFailure)
        assertTrue(repo.gameDir.isDirectory, "el juego anterior debe seguir ahí")
        assertEquals("config", marker.readText(), "el contenido anterior no debe cambiar")
        assertEquals(oldExeSize, File(repo.gameDir, "Reloaded.exe").length())
        assertTrue(
            logs.any { it.contains("restaurado", true) || it.contains("intacto", true) },
            "debe decir qué pasó con el juego anterior: $logs",
        )
        assertFalse(File(home, "game.staging").exists())
        assertTrue(repo.status() is GameRepository.Status.Ready)
    }

    @Test
    fun encuentra_el_exe_dentro_de_una_subcarpeta_y_lo_sube_a_la_raiz() {
        val root = tempDir("anidado")
        val zip = File(root, "juego.zip")
        buildZip(
            zip,
            listOf(
                "Juego/bin/Reloaded.exe" to "exe".toByteArray(),
                "Juego/bin/dll/extra.dll" to "dll".toByteArray(),
                "Juego/data/config.ini" to "config".toByteArray(),
            ),
        )
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertTrue(File(repo.gameDir, "Reloaded.exe").isFile)
        assertTrue(File(repo.gameDir, "data/config.ini").isFile)
        assertTrue(File(repo.gameDir, "dll/extra.dll").isFile, "las dll deben acompañar al exe")
        assertTrue(logs.any { it.contains("bin") }, "debe registrar el movimiento: $logs")
    }

    @Test
    fun una_entrada_duplicada_gana_la_ultima() {
        val root = tempDir("duplicada")
        val zip = File(root, "juego.zip")
        // ZipOutputStream rechaza duplicados: se construye el zip a mano, como el de un
        // empaquetador que añade al archivo existente sin reescribirlo.
        buildRawZip(
            zip,
            listOf(
                "Juego/Reloaded.exe" to "exe".toByteArray(),
                "Juego/data/dup.txt" to "primera".toByteArray(),
                "Juego/data/dup.txt" to "segunda".toByteArray(),
            ),
        )
        val repo = GameRepository(File(root, "home"))
        val logs = mutableListOf<String>()
        val result = import(repo, zip, logs)
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("segunda", File(repo.gameDir, "data/dup.txt").readText())
        assertTrue(logs.any { it.contains("Duplicadas") })
    }

    @Test
    fun acepta_una_entrada_que_declara_tamano_cero() {
        val root = tempDir("cero")
        val zip = File(root, "juego.zip")
        buildZip(
            zip,
            listOf(
                "Reloaded.exe" to "exe".toByteArray(),
                "data/config.ini" to "contenido real".toByteArray(),
            ),
        )
        zeroDeclaredSize(zip, "data/config.ini")
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("contenido real", File(repo.gameDir, "data/config.ini").readText())
    }

    @Test
    fun un_zip_sin_data_falla_con_mensaje_util() {
        val root = tempDir("sindata")
        val zip = File(root, "juego.zip")
        buildZip(zip, listOf("Reloaded.exe" to "exe".toByteArray()))
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("data"), "${result.exceptionOrNull()?.message}")
        assertFalse(repo.gameDir.exists())
    }

    @Test
    fun rechaza_rutas_que_escapan_de_la_carpeta() {
        val root = tempDir("traversal")
        val zip = File(root, "juego.zip")
        buildZip(
            zip,
            listOf(
                "Juego/Reloaded.exe" to "exe".toByteArray(),
                "Juego/data/../../fuera.txt" to "malo".toByteArray(),
            ),
        )
        val repo = GameRepository(File(root, "home"))
        val result = import(repo, zip, mutableListOf())
        assertTrue(result.isFailure)
        assertFalse(File(repo.gameDir, "fuera.txt").exists())
        assertFalse(File(root, "home/fuera.txt").exists())
    }

    @Test
    fun importar_dos_veces_conserva_las_partidas() {
        val root = tempDir("partidas")
        val home = File(root, "home")
        val repo = GameRepository(home)
        val first = File(root, "v1.zip")
        buildZip(first, gameEntries())
        assertTrue(import(repo, first, mutableListOf()).isSuccess)
        val save = File(repo.gameDir, "data/partidas/reloaded1.sav")
        save.writeText("mi partida")

        val second = File(root, "v2.zip")
        buildZip(second, gameEntries().map { (name, data) ->
            if (name == "data/partidas/reloaded1.sav") name to "otra".toByteArray() else name to data
        })
        val result = import(repo, second, mutableListOf())
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("mi partida", save.readText(), "la partida del jugador debe sobrevivir a la actualización")
        assertEquals("config", File(repo.gameDir, "data/config.ini").readText())
    }

    @Test
    fun el_log_rota_si_crece_demasiado() {
        val root = tempDir("log")
        val log = ImportLog(File(root, "app.log"))
        repeat(5) { log.line("x".repeat(900_000)) }
        assertTrue(File(root, "app.log").length() <= 2L * 1024 * 1024, "el log debe rotar")
        assertTrue(File(root, "app.log.1").isFile, "debe quedar la copia rotada")
    }
}
