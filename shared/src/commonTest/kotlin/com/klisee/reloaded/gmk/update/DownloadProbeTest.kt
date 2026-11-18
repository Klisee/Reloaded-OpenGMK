package com.klisee.reloaded.gmk.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadProbeTest {
    private fun sha256(bytes: ByteArray) =
        "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun descarga_verifica_y_deja_el_archivo() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val digest = sha256(payload)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/f.deb") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val root = File("/tmp/opencode/dl-test").apply { deleteRecursively(); mkdirs() }
            val bridge = object : JvmUpdateBridge(root) {
                override val platform = UpdatePlatform.LinuxDeb
                override val installedVersion = "0.6.7"
                override fun userAgent() = "test"
                override suspend fun install(path: String) = false
            }
            val asset = GitHubAsset(
                name = "reloaded-opengmk_0.6.7_amd64.deb",
                size = payload.size.toLong(),
                digest = digest,
                downloadUrl = "http://127.0.0.1:${server.address.port}/f.deb",
            )
            val seen = mutableListOf<Int>()
            val path = runBlocking { bridge.download(asset) { seen += it } }
            val file = File(path)
            assertTrue(file.isFile, "el archivo verificado debe quedar en disco")
            assertEquals(payload.size.toLong(), file.length())
            assertTrue(seen.last() == 100, "el progreso debe terminar en 100")
            assertTrue(seen.size > 2, "debe informar progreso intermedio")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun un_digest_que_no_coincide_se_rechaza_y_no_deja_archivo() {
        val payload = ByteArray(50_000) { 7 }
        val wrong = "sha256:" + "0".repeat(64)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/f.deb") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val root = File("/tmp/opencode/dl-test2").apply { deleteRecursively(); mkdirs() }
            val bridge = object : JvmUpdateBridge(root) {
                override val platform = UpdatePlatform.LinuxDeb
                override val installedVersion = "0.6.7"
                override fun userAgent() = "test"
                override suspend fun install(path: String) = false
            }
            val asset = GitHubAsset(
                name = "x.deb",
                size = payload.size.toLong(),
                digest = wrong,
                downloadUrl = "http://127.0.0.1:${server.address.port}/f.deb",
            )
            val error = runCatching { runBlocking { bridge.download(asset) {} } }.exceptionOrNull()
            assertTrue(error is DigestMismatch, "debe fallar por SHA-256, no por otra cosa: $error")
            assertTrue(File(root, "updates").listFiles().isNullOrEmpty(), "no debe quedar ningún archivo")
        } finally {
            server.stop(0)
        }
    }
}
