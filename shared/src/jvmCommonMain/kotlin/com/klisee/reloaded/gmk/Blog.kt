package com.klisee.reloaded.gmk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object Blog {
    private const val FEED = "https://pokemon-reloaded.blogspot.com/feeds/posts/default?alt=json&max-results=1"

    data class Link(val label: String, val url: String)

    data class Post(
        val title: String,
        val version: String?,
        val links: List<Link>,
        val postUrl: String?,
        val published: String?,
        val images: List<String>,
        val summary: String,
    )

    suspend fun latest(): Result<Post> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        repeat(3) { attempt ->
            if (attempt > 0) delay(700L * attempt)
            runCatching { fetch() }.onSuccess { return@withContext Result.success(it) }.onFailure { last = it }
        }
        Result.failure(last ?: IllegalStateException("No pude consultar el blog."))
    }

    private fun fetch(): Post {
        val connection = (URL(FEED).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ReloadedLauncher/1.0")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) error("El blog respondió ${connection.responseCode}.")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return parse(body)
    }

    private val ANCHOR = Regex("""<a\s[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val VERSION = Regex("""\d+\.\d+(?:\.\d+){1,2}""")
    private val BETA_VERSION = Regex("""Beta\s+(\d+\.\d+(?:\.\d+){1,2})""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""<img[^>]+src="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val DROP_BLOCKS = Regex("""<(script|style)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val PARAGRAPH = Regex("""<br\s*/?>|</p>|</div>|</h\d>""", RegexOption.IGNORE_CASE)
    private val NUMERIC_ENTITY = Regex("""&#(\d+);""")
    private val NAMED_ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&lt;" to "<", "&gt;" to ">", "&aacute;" to "á", "&eacute;" to "é", "&iacute;" to "í",
        "&oacute;" to "ó", "&uacute;" to "ú", "&ntilde;" to "ñ", "&Aacute;" to "Á", "&Eacute;" to "É",
        "&Iacute;" to "Í", "&Oacute;" to "Ó", "&Uacute;" to "Ú", "&Ntilde;" to "Ñ",
        "&iexcl;" to "¡", "&iquest;" to "?", "&laquo;" to "«", "&raquo;" to "»", "&hellip;" to "…",
        "&ndash;" to "–", "&mdash;" to "—", "&middot;" to "·", "&deg;" to "°", "&trade;" to "™",
    )

    private fun parse(body: String): Post {
        val root = Json.parseToJsonElement(body).jsonObject
        val entry = root["feed"]!!.jsonObject["entry"]!!.jsonArray[0].jsonObject
        val title = entry["title"]!!.jsonObject["\$t"]!!.jsonPrimitive.content
        val html = entry["content"]!!.jsonObject["\$t"]!!.jsonPrimitive.content
        val postUrl = entry["link"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["rel"]?.jsonPrimitive?.content == "alternate" }
            ?.get("href")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        val published = entry["published"]?.jsonObject?.get("\$t")?.jsonPrimitive?.content?.take(10)
        val links = downloadLinks(html)
        return Post(
            title = title,
            version = newestVersion(title, links),
            links = links,
            postUrl = postUrl,
            published = published,
            images = images(html),
            summary = summary(html),
        )
    }

    private fun newestVersion(title: String, links: List<Link>): String? {
        val text = (listOf(title) + links.map { it.label }).joinToString(" ")
        return BETA_VERSION.findAll(text).map { it.groupValues[1] }.maxWithOrNull(::compareVersions)
            ?: VERSION.findAll(text).map { it.value }.maxWithOrNull(::compareVersions)
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun downloadLinks(html: String): List<Link> =
        ANCHOR.findAll(html)
            .map { Link(label = TAG.replace(it.groupValues[2], "").trim(), url = it.groupValues[1]) }
            .filter { it.url.contains("mega.nz") || it.url.contains("gofile.io") }
            .distinctBy { it.url }
            .toList()

    private fun images(html: String): List<String> =
        IMAGE.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.startsWith("http") }
            .distinct()
            .toList()

    private fun summary(html: String): String {
        var text = DROP_BLOCKS.replace(html, " ")
        text = PARAGRAPH.replace(text, "\n")
        text = TAG.replace(text, "")
        NAMED_ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
        text = NUMERIC_ENTITY.replace(text) { match -> match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value }
        val paragraphs = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val preview = StringBuilder()
        for (paragraph in paragraphs) {
            if (preview.length + paragraph.length > 700) break
            if (preview.isNotEmpty()) preview.append('\n')
            preview.append(paragraph)
        }
        return preview.toString().ifEmpty { paragraphs.firstOrNull().orEmpty().take(700) }
    }
}
