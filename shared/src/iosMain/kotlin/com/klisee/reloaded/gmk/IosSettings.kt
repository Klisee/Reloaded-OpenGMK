package com.klisee.reloaded.gmk

import platform.Foundation.NSUserDefaults

class IosSettings : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun string(key: String, fallback: String = ""): String =
        defaults.stringForKey(key) ?: fallback

    private fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override var onboardingDone: Boolean
        get() = string("onboardingDone", "0") == "1"
        set(value) = put("onboardingDone", if (value) "1" else "0")

    override var theme: String
        get() = string("theme", "liquid")
        set(value) = put("theme", value)

    override var darkMode: String
        get() = when (string("darkMode", "auto")) {
            "light", "dark" -> string("darkMode", "auto")
            else -> "auto"
        }
        set(value) = put("darkMode", if (value == "light" || value == "dark") value else "auto")

    override var language: String
        get() = string("language", "auto")
        set(value) = put("language", value)

    override var fpsMode: String
        get() = string("fpsMode", "original")
        set(value) = put("fpsMode", value)

    override var touchStyle: String
        get() = string("touchStyle", "classic")
        set(value) = put("touchStyle", value)

    override var moveMode: String
        get() = string("moveMode", "joystick")
        set(value) = put("moveMode", value)

    override var touchScale: Float
        get() = string("touchScale", "1.0").toFloatOrNull() ?: 1f
        set(value) = put("touchScale", value.toString())

    override var touchOpacity: Float
        get() = string("touchOpacity", "0.5").toFloatOrNull() ?: 0.5f
        set(value) = put("touchOpacity", value.toString())

    override var keepAspect: Boolean
        get() = string("keepAspect", "0") == "1"
        set(value) = put("keepAspect", if (value) "1" else "0")

    override var showFps: Boolean
        get() = string("showFps", "0") == "1"
        set(value) = put("showFps", if (value) "1" else "0")

    override var renderer: String
        get() = when (string("renderer", "auto")) {
            "opengl", "vulkan" -> string("renderer", "auto")
            else -> "auto"
        }
        set(value) = put("renderer", if (value == "opengl" || value == "vulkan") value else "auto")

    override var modsEnabled: Boolean
        get() = string("modsEnabled", "0") == "1"
        set(value) = put("modsEnabled", if (value) "1" else "0")

    override var controlBindings: Map<ControlAction, Int>
        get() = ControlAction.entries.mapNotNull { action ->
            string("control.${action.name}").toIntOrNull()?.let { action to it }
        }.toMap()
        set(value) {
            ControlAction.entries.forEach { action ->
                val code = value[action]
                if (code == null) defaults.removeObjectForKey("control.${action.name}")
                else put("control.${action.name}", code.toString())
            }
        }

    override var stickEnabled: Boolean
        get() = string("stickEnabled", "1") == "1"
        set(value) = put("stickEnabled", if (value) "1" else "0")

    override var keepScreenOn: Boolean
        get() = string("keepScreenOn", "1") == "1"
        set(value) = put("keepScreenOn", if (value) "1" else "0")

    override var touchLayouts: Map<String, TouchPlacement>
        get() = runCatching { TouchLayouts.parse(string("touchLayouts")) }.getOrDefault(emptyMap())
        set(value) = put("touchLayouts", TouchLayouts.encode(value))

    override var touchAppearance: TouchAppearance
        get() = runCatching { TouchAppearance.parse(string("touchAppearance")) }.getOrDefault(TouchAppearance())
        set(value) = put("touchAppearance", value.encode())

    override var touchSprites: Map<String, String>
        get() = string("touchSprites").lineSequence().mapNotNull { line ->
            val id = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim()
            if (id.isEmpty() || value.isEmpty()) null else id to value
        }.toMap()
        set(value) = put("touchSprites", value.entries.joinToString("\n") { "${it.key}=${it.value}" })

    override fun writeControls(bindings: Map<ControlAction, Int>, stick: Boolean, padConnected: Boolean) {
        val text = buildString {
            for (action in ControlAction.entries) {
                bindings[action]?.let { appendLine("$it=${action.key}") }
            }
            appendLine("stick=${if (stick) 1 else 0}")
            appendLine("pad=${if (padConnected) 1 else 0}")
        }
        writeText("controls.conf", text)
    }

    override fun stageSprite(id: String, bytes: ByteArray): String {
        val hash = bytes.size.toString(16) + bytes.contentHashCode().toString(16)
        touchSprites = touchSprites + (id to hash)
        return hash
    }

    override fun resetSprite(id: String) {
        touchSprites = if ('/' in id) touchSprites + (id to "original") else touchSprites - id
    }

    override fun clearSprites() {
        touchSprites = emptyMap()
    }

    override fun writeText(name: String, text: String) {
        put("file.$name", text)
    }
}
