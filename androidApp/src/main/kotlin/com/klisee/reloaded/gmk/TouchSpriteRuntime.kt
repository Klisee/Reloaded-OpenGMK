package com.klisee.reloaded.gmk

import android.content.res.AssetManager
import java.io.File

object TouchSpriteRuntime {
    fun writeRuntime(root: File, assets: AssetManager, values: Map<String, String>) {
        val dir = File(root, "touch-sprites-active").apply { mkdirs() }
        for (id in TouchSpriteCatalog.keys) {
            val name = id.replace("/", "__")
            val marker = File(dir, "$name.original")
            if (values[id] == "original") {
                marker.writeBytes(byteArrayOf(1))
            } else {
                marker.delete()
            }
            val out = File(dir, "$name.png")
            val hash = values[id]?.takeIf { it != "original" }
            val source = hash?.let { File(root, "touch-sprites/$it.png") }
            if (source != null && source.isFile) {
                source.copyTo(out, overwrite = true)
            } else {
                out.delete()
            }
        }
    }
}
