package com.klisee.reloaded.gmk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object Update {
    private const val REPO = "Klisee/Reloaded-OpenGMK"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    data class Release(val version: String, val pageUrl: String)

    @Suppress("UNUSED_PARAMETER")
    suspend fun check(installed: String, abis: List<String>): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val root = Json.parseToJsonElement(get(LATEST)).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content.orEmpty().trim().trimStart('v', 'V')
            if (tag.isEmpty() || !isNewer(tag, installed)) return@runCatching null
            Release(tag, root["html_url"]?.jsonPrimitive?.content?.ifEmpty { RELEASES_PAGE } ?: RELEASES_PAGE)
        }
    }

    fun isNewer(candidate: String, installed: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(installed)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun numbers(value: String) = value.trim().split('.', '-', '+').mapNotNull { it.toIntOrNull() }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ReloadedLauncher/1.0")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}.")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
