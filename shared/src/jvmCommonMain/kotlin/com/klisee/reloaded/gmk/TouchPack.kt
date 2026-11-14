package com.klisee.reloaded.gmk

import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object TouchPack {
    private const val FORMAT = "reloaded-opengmk-touch"
    private const val VERSION = "1"

    data class Pack(
        val layouts: Map<String, TouchPlacement>,
        val appearance: TouchAppearance,
        val sprites: Map<String, ByteArray>,
        val opacity: Float = .5f,
        val scale: Float = 1f,
    )

    fun export(settings: AppSettings, includeSprites: Boolean): File {
        val output = File(settings.exportDir(), "controles.zip")
        output.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty("format", FORMAT)
            setProperty("version", VERSION)
            setProperty("layouts", TouchLayouts.encode(settings.touchLayouts))
            setProperty("appearance", settings.touchAppearance.encode())
            setProperty("opacity", settings.touchOpacity.toString())
            setProperty("scale", settings.touchScale.toString())
            setProperty("includesSprites", includeSprites.toString())
        }
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.properties"))
            properties.store(zip, "Reloaded OpenGMK touch controls")
            zip.closeEntry()
            if (includeSprites) {
                for ((id, value) in settings.touchSprites) {
                    if (value == "original") continue
                    val file = settings.spriteFile(value)
                    if (!file.isFile) continue
                    zip.putNextEntry(ZipEntry("sprites/${id.replace("/", "__")}.png"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return output
    }

    fun read(file: File): Pack {
        var manifest: Properties? = null
        val sprites = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    if (name == "manifest.properties") {
                        val text = zip.readBytes().toString(Charsets.ISO_8859_1)
                        manifest = Properties().apply { load(text.reader()) }
                    } else if (name.startsWith("sprites/") && name.endsWith(".png")) {
                        val id = name.removePrefix("sprites/").removeSuffix(".png").replace("__", "/")
                        sprites[id] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val values = manifest ?: error("El paquete no tiene manifiesto.")
        if (values.getProperty("format") != FORMAT) error("El paquete no es de Reloaded OpenGMK.")
        val layoutsText = values.getProperty("layouts").orEmpty()
        val appearanceText = values.getProperty("appearance").orEmpty()
        return Pack(
            layouts = TouchLayouts.parse(layoutsText),
            appearance = TouchAppearance.parse(appearanceText),
            sprites = sprites,
            opacity = values.getProperty("opacity")?.toFloatOrNull()?.coerceIn(.05f, 1f) ?: .5f,
            scale = values.getProperty("scale")?.toFloatOrNull()?.coerceIn(.5f, 2f) ?: 1f,
        )
    }

    fun apply(settings: AppSettings, pack: Pack, applySettings: Boolean, applySprites: Boolean) {
        if (applySettings) {
            settings.touchLayouts = pack.layouts
            settings.touchAppearance = pack.appearance
            settings.touchOpacity = pack.opacity
            settings.touchScale = pack.scale
        }
        if (applySprites) {
            for ((id, bytes) in pack.sprites) settings.stageSprite(id, bytes)
        }
    }
}
