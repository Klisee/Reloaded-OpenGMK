package com.klisee.reloaded.gmk

import android.content.Context
import java.io.File

/** Converts the launcher data kept by Pokemon Reloaded Android 0.4.x. */
internal object LegacyMigration {
    private const val MARKER = "legacy-migration-v1.done"

    fun migrate(context: Context, filesDir: File) {
        val marker = File(filesDir, MARKER)
        if (marker.isFile) return

        runCatching {
            val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            if (File(filesDir, "settings.properties").isFile) {
                marker.writeText("settings.properties ya existia\n")
                return@runCatching
            }

            val settings = AppSettings(filesDir)
            if (prefs.contains("onboardingDone")) settings.onboardingDone = prefs.getBoolean("onboardingDone", false)
            if (prefs.contains("keepScreenOn")) settings.keepScreenOn = prefs.getBoolean("keepScreenOn", true)
            if (prefs.contains("stickEnabled")) settings.stickEnabled = prefs.getBoolean("stickEnabled", true)
            if (prefs.contains("keepAspect")) settings.keepAspect = prefs.getBoolean("keepAspect", false)
            if (prefs.contains("showFps")) settings.showFps = prefs.getBoolean("showFps", false)
            if (prefs.contains("modsEnabled")) settings.modsEnabled = prefs.getBoolean("modsEnabled", false)
            if (prefs.contains("touchOpacity")) settings.touchOpacity = prefs.getFloat("touchOpacity", .5f)
            if (prefs.contains("touchScale")) settings.touchScale = prefs.getFloat("touchScale", 1f)
            prefs.getString("moveMode", null)?.let { settings.moveMode = it }
            prefs.getString("touchStyle", null)?.let { settings.touchStyle = if (it == "none") "none" else "classic" }
            prefs.getString("gameLanguage", null)?.let { settings.language = it }
            prefs.getString("renderMode", null)?.let { mode ->
                settings.fpsMode = when (mode) {
                    "60", "120", "uncapped" -> mode
                    else -> "original"
                }
            }

            val bindings = prefs.getStringSet("controlBindings", emptySet()).orEmpty().mapNotNull { entry ->
                val parts = entry.split('=')
                val action = ControlAction.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
                val code = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                action to code
            }.toMap()
            if (bindings.isNotEmpty()) settings.controlBindings = bindings

            prefs.getString("touchLayoutV1", null)?.let { text ->
                val layouts = TouchLayouts.parse(text)
                if (layouts.isNotEmpty()) settings.touchLayouts = layouts
            }
            prefs.getString("touchAppearanceV1", null)?.let { text ->
                if (text.isNotBlank()) settings.touchAppearance = TouchAppearance.parse(text)
            }
            prefs.getString("touchSpritesV1", null)?.let { text ->
                val sprites = TouchSprites.parse(text)
                if (sprites.isNotEmpty()) settings.touchSprites = sprites
            }

            marker.writeText("migrado\n")
        }.onFailure { failure ->
            File(filesDir, "legacy-migration.log").writeText(failure.stackTraceToString())
        }
    }
}
