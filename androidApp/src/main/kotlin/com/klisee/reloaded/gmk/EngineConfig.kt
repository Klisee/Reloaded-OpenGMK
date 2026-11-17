package com.klisee.reloaded.gmk

import android.content.res.AssetManager
import java.io.File
import kotlin.math.roundToInt

object EngineConfig {
    fun write(
        root: File,
        settings: AppSettings,
        assets: AssetManager,
        padConnected: Boolean,
        refreshRate: Float = 60f,
    ) {
        root.mkdirs()
        val scaling = if (settings.keepAspect) "aspect" else "full"
        val detectedHz = refreshRate.roundToInt().coerceAtLeast(60)
        val render = when (settings.fpsMode) {
            "60" -> "60"
            "120" -> "120"
            "uncapped" -> detectedHz.toString()
            else -> "native"
        }
        val showFps = if (settings.showFps) "1" else "0"
        File(root, "video.conf").writeText(
            buildString {
                appendLine("scaling=$scaling")
                appendLine("render=$render")
                appendLine("show_fps=$showFps")
                appendLine("renderer=${settings.renderer}")
            },
        )
        settings.writeControls(settings.controlBindings, settings.stickEnabled, padConnected)
        val touchStyle = if (settings.touchStyle == "none") "none" else "classic"
        File(root, "touch.conf").writeText(
            buildString {
                appendLine("style=$touchStyle")
                appendLine("move=${settings.moveMode}")
                appendLine("scale=${settings.touchScale}")
                appendLine("opacity=${settings.touchOpacity}")
            },
        )
        File(root, "touch-layout.conf").writeText(com.klisee.reloaded.gmk.TouchLayouts.encode(settings.touchLayouts))
        File(root, "touch-appearance.conf").writeText(settings.touchAppearance.encode())
        File(root, "game/data/text").mkdirs()
        val language = when (settings.language) {
            "es", "en" -> settings.language
            else -> if (java.util.Locale.getDefault().language == "es") "es" else "en"
        }
        File(root, "game/data/text/config.ini").writeText(if (language == "en") " 1" else " 0")
        TouchLabels.writeRuntime(root)
        TouchSpriteRuntime.writeRuntime(root, assets, settings.touchSprites)
    }

    fun copyAssets(root: File, assets: AssetManager) {
        root.mkdirs()
        val names = runCatching { assets.list("")?.toList() }.getOrNull().orEmpty()
        for (name in names) {
            if (!name.endsWith(".png", true) && name != "soundfont.sf2") continue
            runCatching {
                assets.open(name).use { input ->
                    File(root, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        val soundfont = File(root, "soundfont.sf2")
        if (soundfont.isFile) {
            val game = File(root, "game")
            if (game.isDirectory) soundfont.copyTo(File(game, "soundfont.sf2"), overwrite = true)
        }
    }
}
