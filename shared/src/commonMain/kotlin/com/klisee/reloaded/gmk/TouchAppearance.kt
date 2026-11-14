package com.klisee.reloaded.gmk


enum class TouchStyle(val id: String, val title: String) {
    ORIGINAL("original", "Original"),
    MINIMAL("minimal", "Minimalista"),
    PIXEL("pixel", "Pixel"),
}

enum class TouchStrength(val title: String, val scale: Float) {
    SOFT("Suave", .5f), MEDIUM("Media", .7f), STRONG("Fuerte", 1f)
}

data class TouchAppearance(
    val style: TouchStyle = TouchStyle.ORIGINAL,
    val colorHex: String = "original",
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val strength: TouchStrength = TouchStrength.SOFT,
) {
    val tint: Int? get() = colorHex.takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }?.toIntOrNull(16)

    fun encode(): String = buildString {
        appendLine("touch-appearance-v1")
        appendLine("style=${style.id}")
        appendLine("tint=${tint?.let { it.toString(16).uppercase().padStart(6, '0') } ?: "original"}")
        appendLine("animations=${if (animations) 1 else 0}")
        appendLine("haptics=${if (haptics) 1 else 0}")
        appendLine("strength=${strength.name}")
    }

    companion object {
        fun parse(text: String): TouchAppearance {
            if (text.length > 4096 || text.lineSequence().firstOrNull() != "touch-appearance-v1") return TouchAppearance()
            val fields = text.lineSequence().drop(1).mapNotNull { line ->
                line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            return TouchAppearance(
                style = TouchStyle.entries.firstOrNull { it.id == fields["style"] } ?: TouchStyle.ORIGINAL,
                colorHex = fields["tint"]?.takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }?.uppercase() ?: "original",
                animations = fields["animations"] != "0",
                haptics = fields["haptics"] != "0",
                strength = TouchStrength.entries.firstOrNull { it.name == fields["strength"] } ?: TouchStrength.SOFT,
            )
        }
    }
}
