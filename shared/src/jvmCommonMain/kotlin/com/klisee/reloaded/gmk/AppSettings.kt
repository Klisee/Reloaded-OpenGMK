package com.klisee.reloaded.gmk

import java.io.File
import java.util.Properties

class AppSettings(private val root: File) : SettingsStore {
    private val file = File(root, "settings.properties")
    private val values = Properties().apply {
        if (file.isFile) runCatching { file.inputStream().use { load(it) } }
    }

    override var onboardingDone: Boolean
        get() = values.getProperty("onboardingDone", "0") == "1"
        set(value) = put("onboardingDone", if (value) "1" else "0")

    override var theme: String
        get() = values.getProperty("theme", "material")
        set(value) = put("theme", value)

    override var language: String
        get() = values.getProperty("language", "auto")
        set(value) = put("language", value)

    override var fpsMode: String
        get() = values.getProperty("fpsMode", "original")
        set(value) = put("fpsMode", value)

    override var touchStyle: String
        get() = when (values.getProperty("touchStyle", "classic")) {
            "none" -> "none"
            else -> "classic"
        }
        set(value) = put("touchStyle", if (value == "none") "none" else "classic")

    override var moveMode: String
        get() = values.getProperty("moveMode", "joystick")
        set(value) = put("moveMode", value)

    override var touchScale: Float
        get() = values.getProperty("touchScale", "1.0").toFloatOrNull() ?: 1f
        set(value) = put("touchScale", value.toString())

    override var touchOpacity: Float
        get() = values.getProperty("touchOpacity", "0.5").toFloatOrNull() ?: 0.5f
        set(value) = put("touchOpacity", value.toString())

    override var keepAspect: Boolean
        get() = values.getProperty("keepAspect", "0") == "1"
        set(value) = put("keepAspect", if (value) "1" else "0")

    override var showFps: Boolean
        get() = values.getProperty("showFps", "0") == "1"
        set(value) = put("showFps", if (value) "1" else "0")

    override var modsEnabled: Boolean
        get() = values.getProperty("modsEnabled", "0") == "1"
        set(value) = put("modsEnabled", if (value) "1" else "0")

    override var controlBindings: Map<ControlAction, Int>
        get() = ControlAction.entries.mapNotNull { action ->
            values.getProperty("control.${action.name}")?.toIntOrNull()?.let { action to it }
        }.toMap()
        set(value) {
            ControlAction.entries.forEach { action ->
                val key = "control.${action.name}"
                val code = value[action]
                if (code == null) values.remove(key) else values.setProperty(key, code.toString())
            }
            root.mkdirs()
            runCatching { file.outputStream().use { values.store(it, "Reloaded OpenGMK") } }
        }

    override var stickEnabled: Boolean
        get() = values.getProperty("stickEnabled", "1") == "1"
        set(value) = put("stickEnabled", if (value) "1" else "0")

    override fun writeControls(bindings: Map<ControlAction, Int>, stick: Boolean, padConnected: Boolean) {
        // Una línea por acción: `origen=destino`.
        // - Android: origen = código de botón del mando (96 = A, 97 = B, …), destino = vk del juego.
        // - Escritorio: origen = código vk de la tecla física que se pulsa, destino = vk del juego.
        val text = buildString {
            appendLine("# Escrito por la pantalla de Controles. El motor lo lee al arrancar.")
            appendLine("# origen=destino  (origen: botón de mando o tecla física; destino: tecla del juego)")
            for (action in ControlAction.entries) {
                val source = bindings[action] ?: action.defaultSourceKey
                appendLine("$source=${action.engineKeyCode}")
            }
            appendLine("stick=${if (stick) 1 else 0}")
            appendLine("pad=${if (padConnected) 1 else 0}")
        }
        writeText("controls.conf", text)
    }

    override var keepScreenOn: Boolean
        get() = values.getProperty("keepScreenOn", "1") == "1"
        set(value) = put("keepScreenOn", if (value) "1" else "0")

    override var touchLayouts: Map<String, TouchPlacement>
        get() = runCatching { TouchLayouts.parse(File(root, "touch-layout.conf").readText()) }.getOrDefault(emptyMap())
        set(value) = writeText("touch-layout.conf", TouchLayouts.encode(value))

    override var touchAppearance: TouchAppearance
        get() = runCatching { TouchAppearance.parse(File(root, "touch-appearance.conf").readText()) }.getOrDefault(TouchAppearance())
        set(value) = writeText("touch-appearance.conf", value.encode())

    override var touchSprites: Map<String, String>
        get() = runCatching {
            File(root, "touch-sprites.conf").readLines().mapNotNull { line ->
                val id = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim()
                if (id.isEmpty() || value.isEmpty()) null else id to value
            }.toMap()
        }.getOrDefault(emptyMap())
        set(value) = writeText("touch-sprites.conf", value.entries.joinToString("\n") { "${it.key}=${it.value}" })

    fun spriteFile(hash: String): File = File(root, "touch-sprites/$hash.png")

    override fun stageSprite(id: String, bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "La imagen está vacía." }
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        val target = spriteFile(hash)
        target.parentFile?.mkdirs()
        if (!target.isFile) target.writeBytes(bytes)
        touchSprites = touchSprites + (id to hash)
        return hash
    }

    override fun resetSprite(id: String) {
        touchSprites = if ('/' in id) touchSprites + (id to "original") else touchSprites - id
    }

    override fun clearSprites() {
        touchSprites = emptyMap()
    }

    fun exportDir(): File = File(root, "exports").apply { mkdirs() }

    override fun writeText(name: String, text: String) {
        root.mkdirs()
        runCatching { File(root, name).writeText(text) }
    }

    private fun put(key: String, value: String) {
        values.setProperty(key, value)
        root.mkdirs()
        runCatching { file.outputStream().use { values.store(it, "Reloaded OpenGMK") } }
    }
}
