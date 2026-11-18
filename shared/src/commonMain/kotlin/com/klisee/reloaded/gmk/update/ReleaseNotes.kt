package com.klisee.reloaded.gmk.update

/**
 * Convierte las notas de una release de GitHub a algo que Compose pueda pintar.
 *
 * No es un parser de Markdown completo: entiende lo que de verdad aparece en las notas de este
 * proyecto (títulos, listas, negritas, código, enlaces y separadores) y deja el resto como texto.
 * Evita meter una librería de Markdown entera solo para una pantalla.
 */
sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock
    data class Paragraph(val spans: List<NoteSpan>) : NoteBlock
    data class Bullet(val spans: List<NoteSpan>, val indent: Int, val ordered: Boolean, val marker: String?) : NoteBlock
    data class Code(val text: String) : NoteBlock
    data object Divider : NoteBlock
}

/** Trozos con estilo dentro de un párrafo o una viñeta. */
sealed interface NoteSpan {
    data class Text(val value: String) : NoteSpan
    data class Bold(val value: String) : NoteSpan
    data class Italic(val value: String) : NoteSpan
    data class Code(val value: String) : NoteSpan
    data class Link(val value: String, val url: String) : NoteSpan
}

fun parseReleaseNotes(markdown: String): List<NoteBlock> {
    val blocks = mutableListOf<NoteBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val text = paragraph.joinToString(" ").trim()
        paragraph.clear()
        if (text.isNotEmpty()) blocks += NoteBlock.Paragraph(spansOf(text))
    }

    fun flushCode() {
        if (code.isEmpty()) return
        blocks += NoteBlock.Code(code.joinToString("\n").trimEnd())
        code.clear()
    }

    for (raw in markdown.lineSequence()) {
        val line = raw.trimEnd()
        when {
            line.trimStart().startsWith("```") -> {
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    flushParagraph()
                    inCode = true
                }
            }

            inCode -> code += raw

            line.isBlank() -> flushParagraph()

            line.trim() in setOf("---", "***", "___") -> {
                flushParagraph()
                blocks += NoteBlock.Divider
            }

            headingLevel(line) > 0 -> {
                flushParagraph()
                val level = headingLevel(line)
                blocks += NoteBlock.Heading(level, line.trimStart().drop(level).trim().replace(Regex("\\s+#+$"), ""))
            }

            bulletOf(line) != null -> {
                flushParagraph()
                val bullet = bulletOf(line)!!
                blocks += NoteBlock.Bullet(
                    spans = spansOf(bullet.text),
                    indent = bullet.indent,
                    ordered = bullet.ordered,
                    marker = bullet.marker,
                )
            }

            else -> paragraph += line.trim()
        }
    }
    if (inCode) flushCode()
    flushParagraph()
    return blocks
}

private class BulletLine(val indent: Int, val ordered: Boolean, val marker: String?, val text: String)

private fun headingLevel(line: String): Int {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith("#")) return 0
    val level = trimmed.takeWhile { it == '#' }.length
    return if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') level else 0
}

private fun bulletOf(line: String): BulletLine? {
    val indent = (line.length - line.trimStart().length) / 2
    val trimmed = line.trimStart()
    val unordered = listOf("- ", "* ", "+ ").firstOrNull { trimmed.startsWith(it) }
    if (unordered != null) {
        return BulletLine(indent, ordered = false, marker = null, text = trimmed.removePrefix(unordered))
    }
    val ordered = Regex("^(\\d{1,3})[.)]\\s+").find(trimmed) ?: return null
    return BulletLine(indent, ordered = true, marker = ordered.groupValues[1], text = trimmed.substring(ordered.range.last + 1))
}

/**
 * Parte una línea en trozos con estilo. El orden importa: primero los enlaces (que pueden contener
 * negritas), luego el código en línea, y al final negritas e itálicas sobre el texto suelto.
 */
internal fun spansOf(text: String): List<NoteSpan> {
    if (text.isEmpty()) return emptyList()
    val spans = mutableListOf<NoteSpan>()
    var index = 0

    fun pushPlain(value: String) {
        if (value.isEmpty()) return
        // Negritas e itálicas dentro del texto suelto, en una pasada.
        var cursor = 0
        for (match in Regex("(\\*\\*|__)(.+?)\\1|(\\*|_)(.+?)\\3").findAll(value)) {
            if (match.range.first > cursor) spans += NoteSpan.Text(value.substring(cursor, match.range.first))
            val bold = match.groupValues[2]
            if (bold.isNotEmpty()) {
                spans += NoteSpan.Bold(bold)
            } else {
                spans += NoteSpan.Italic(match.groupValues[4])
            }
            cursor = match.range.last + 1
        }
        if (cursor < value.length) spans += NoteSpan.Text(value.substring(cursor))
    }

    // Los marcadores de código resaltan al revés, así que se recorren mezclados con los enlaces.
    val pattern = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)|`([^`]+)`")
    for (match in pattern.findAll(text)) {
        if (match.range.first > index) pushPlain(text.substring(index, match.range.first))
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        val code = match.groupValues[3]
        when {
            code.isNotEmpty() -> spans += NoteSpan.Code(code)
            label.isNotEmpty() -> spans += NoteSpan.Link(label, url)
        }
        index = match.range.last + 1
    }
    if (index < text.length) pushPlain(text.substring(index))
    return spans
}

/** Texto plano de las notas, para la vista previa de una línea. */
fun notesPlainPreview(markdown: String, limit: Int = 140): String {
    val plain = markdown
        // Los bloques y enlaces se sustituyen por su texto antes de quitar los símbolos, o el
        // contenido de un enlace se pierde y quedan guiones sueltos dentro de las palabras.
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*\\*|__"), "")
        // Solo los marcadores al principio de línea son de formato; un guion dentro de una palabra
        // (SHA-256) es parte del texto y tiene que quedarse.
        .replace(Regex("(?m)^\\s{0,3}(#{1,6}|>|[-*+])\\s+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (plain.length <= limit) plain else plain.take(limit).trimEnd() + "…"
}
