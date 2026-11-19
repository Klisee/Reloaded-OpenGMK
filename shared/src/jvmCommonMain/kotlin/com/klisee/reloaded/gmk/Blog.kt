package com.klisee.reloaded.gmk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Blog {
    /** Índice del blog propio. Se lee de aquí y no de un feed: klisee.com no publica RSS. */
    private const val INDEX = "https://klisee.com/blog"

    /** Enlace exacto de descarga del juego en el blog de F3R. */
    const val GAME_DOWNLOAD_URL = "https://pokemon-reloaded.blogspot.com/2026/08/the-last-beta-192.html"

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
        val connection = (URL(INDEX).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ReloadedLauncher/1.0")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) error("El blog respondió ${connection.responseCode}.")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return parse(body)
    }

    private val TAG = Regex("<[^>]+>")
    private val VERSION = Regex("""\d+\.\d+(?:\.\d+){1,2}""")
    private val BETA_VERSION = Regex("""Beta\s+(\d+\.\d+(?:\.\d+){1,2})""", RegexOption.IGNORE_CASE)
    private val NUMERIC_ENTITY = Regex("""&#(\d+);""")
    private val NAMED_ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&lt;" to "<", "&gt;" to ">", "&aacute;" to "á", "&eacute;" to "é", "&iacute;" to "í",
        "&oacute;" to "ó", "&uacute;" to "ú", "&ntilde;" to "ñ", "&Aacute;" to "Á", "&Eacute;" to "É",
        "&Iacute;" to "Í", "&Oacute;" to "Ó", "&Uacute;" to "Ú", "&Ntilde;" to "Ñ",
        "&iexcl;" to "¡", "&iquest;" to "?", "&laquo;" to "«", "&raquo;" to "»", "&hellip;" to "…",
        "&ndash;" to "–", "&mdash;" to "—", "&middot;" to "·", "&deg;" to "°", "&trade;" to "™",
    )

    /**
     * Lee la tarjeta destacada del listado del blog (o la primera entrada de la lista).
     *
     * klisee.com es un sitio Next.js: no hay RSS ni JSON público, así que se lee el HTML. Lo que
     * interesa está en tres sitios estables de la tarjeta: el enlace `/blog/<slug>`, el `<h2>` con
     * el título y el `<time>` con la fecha. El resumen sale del extracto de la tarjeta.
     */
    private fun parse(body: String): Post {
        val card = Regex("""<a[^>]+href="(/blog/[^"#?]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(body)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .firstOrNull { (href, _) ->
                // El propio listado enlaza a "/blog" y a otras secciones: solo sirven los posts.
                href.removePrefix("/blog/").isNotBlank() && !href.endsWith(".png")
            }
            ?: error("El blog no tiene entradas.")

        val (path, cardHtml) = card
        val title = Regex("""<h[12][^>]*>(.*?)</h[12]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cardHtml)?.groupValues?.get(1)?.let(::plainText)
            ?.takeIf { it.isNotBlank() }
            ?: error("La entrada del blog no tiene título.")
        val published = Regex("""<time[^>]*>(.*?)</time>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cardHtml)?.groupValues?.get(1)?.let(::plainText)
            ?.takeIf { it.isNotBlank() }
            ?.let(::toIsoDate)
        val excerpt = Regex("""class="[^"]*cardExcerpt[^"]*"[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cardHtml)?.groupValues?.get(1)?.let(::plainText)
            ?.takeIf { it.isNotBlank() }
        val cover = Regex("""url\(&#x27;(/[^&]+)&#x27;\)|url\('(/[^']+)'\)""")
            .find(cardHtml)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }

        val postUrl = "https://klisee.com$path"
        // La descarga va siempre al blog de F3R, y el botón de la portada usa este mismo enlace.
        val download = Link(label = "Descargar Pokémon Reloaded", url = GAME_DOWNLOAD_URL)
        return Post(
            title = title,
            version = newestVersion(title, listOf(download)),
            links = listOf(download),
            postUrl = postUrl,
            published = published,
            images = listOfNotNull(cover?.let { "https://klisee.com$it" }),
            summary = excerpt ?: "",
        )
    }

    private val MONTHS = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
    )

    /**
     * El blog escribe la fecha en castellano («18 de noviembre de 2026»). El resto de la app
     * trabaja con ISO (`yyyy-MM-dd`), así que se convierte aquí y no en cada plataforma: Android,
     * escritorio e iOS tienen formateadores distintos y ninguno entiende el texto del blog.
     */
    private fun toIsoDate(text: String): String? {
        val iso = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(text)
        if (iso != null) return iso.value
        val match = Regex("""(\d{1,2})\s+de\s+([a-záéíóúñ]+)\s+de\s+(\d{4})""", RegexOption.IGNORE_CASE).find(text)
        if (match != null) {
            val day = match.groupValues[1].toIntOrNull()
            val month = MONTHS[match.groupValues[2].lowercase()]
            val year = match.groupValues[3].toIntOrNull()
            if (day != null && month != null && year != null) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        // dd/mm/aaaa, por si el sitio cambia de formato.
        val slash = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").find(text)
        if (slash != null) {
            return "%04d-%02d-%02d".format(
                slash.groupValues[3].toInt(),
                slash.groupValues[2].toInt(),
                slash.groupValues[1].toInt(),
            )
        }
        return null
    }

    /** Quita etiquetas y decodifica las entidades que usa el sitio. */
    private fun plainText(html: String): String {
        var text = TAG.replace(html, "")
        NAMED_ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
        text = NUMERIC_ENTITY.replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
        }
        return text.replace(Regex("\\s+"), " ").trim()
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
}
