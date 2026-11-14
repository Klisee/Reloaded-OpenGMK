import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("dev.nucleusframework") version "2.5.18"
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("dev.nucleusframework.composefluent:fluent:1.0.0")
    implementation("io.github.vinceglb:filekit-dialogs:0.17.0")
}

nucleus.application {
    mainClass = "com.klisee.reloaded.gmk.MainKt"
    jvmArgs("-Djava.awt.headless=false")
    // FileKit usa JAAS por reflexión (Polkit/portal en Linux). Sin este módulo el runtime
    // recortado por jpackage no lo encuentra y el selector de archivos muere con
    // NoClassDefFoundError: com/sun/security/auth/module/UnixSystem.
    nativeDistributions {
        modules(
            "jdk.security.auth",
            "jdk.crypto.ec",
            "java.naming",
            "jdk.unsupported",
        )
    }
    nativeDistributions {
        targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Nsis)
        packageName = "reloaded-opengmk"
        packageVersion = "0.6.7"
        description = "Reloaded OpenGMK: Pokemon Reloaded for Linux, Windows and Android"
        vendor = "Klisee"
        copyright = "Comunidad, sin animo de lucro"
        homepage = "https://github.com/Klisee/Reloaded-OpenGMK"
        linux {
            debMaintainer = "Klisee <klisee@users.noreply.github.com>"
            iconFile.set(project.file("icons/reloaded-opengmk.png"))
            packageVersion = "0.6.7"
            appCategory = "Game"
            appRelease = "1"
            startupWMClass = "com-klisee-reloaded-gmk-MainKt"
        }
        windows {
            iconFile.set(project.file("icons/reloaded-opengmk.ico"))
        }
    }
}

// Nucleus no permite cambiar el `Name=` del lanzador en Linux: usa `packageName` tal cual.
// Se parchea el .desktop ya empaquetado para que el menú muestre "Reloaded OpenGMK".
val displayName = "Reloaded OpenGMK"

tasks.configureEach {
    if (name == "packageDeb") {
        doLast {
            val debDir = layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
            val deb = debDir.listFiles { f -> f.extension == "deb" }?.maxByOrNull { it.lastModified() } ?: return@doLast
            val work = layout.buildDirectory.dir("tmp/deb-rename").get().asFile
            work.deleteRecursively()
            work.mkdirs()

            fun exec(vararg cmd: String): Int {
                val p = ProcessBuilder(*cmd).directory(work).redirectErrorStream(true).start()
                p.inputStream.bufferedReader().forEachLine { println(it) }
                return p.waitFor()
            }

            if (exec("ar", "x", deb.absolutePath) != 0) return@doLast
            val data = work.listFiles { f -> f.name.startsWith("data.tar") }?.firstOrNull() ?: return@doLast
            val control = work.listFiles { f -> f.name.startsWith("control.tar") }?.firstOrNull() ?: return@doLast
            val ext = data.name.substringAfter("data.tar")
            val cext = control.name.substringAfter("control.tar")

            work.resolve("data").mkdirs()
            work.resolve("control").mkdirs()
            if (exec("tar", "xf", data.absolutePath, "-C", work.resolve("data").absolutePath) != 0) return@doLast
            if (exec("tar", "xf", control.absolutePath, "-C", work.resolve("control").absolutePath) != 0) return@doLast

            val desktop = work.resolve("data/usr/share/applications/reloaded-opengmk.desktop")
            if (desktop.isFile) {
                var text = desktop.readText()
                if (!text.lineSequence().any { it.startsWith("Name=") }) {
                    text = "Name=$displayName\n$text"
                } else {
                    text = text.lineSequence().joinToString("\n") { line ->
                        if (line.startsWith("Name=")) "Name=$displayName" else line
                    }
                }
                if (!text.contains("Keywords=")) {
                    text = text.trimEnd() + "\nKeywords=Pokemon;Reloaded;GameMaker;OpenGMK;\n"
                }
                desktop.writeText(text)
                println("lanzador renombrado a \"$displayName\"")
            }

            data.delete()
            control.delete()
            if (exec("tar", "cJf", "data.tar$ext", "-C", work.resolve("data").absolutePath, ".") != 0) return@doLast
            if (exec("tar", "cJf", "control.tar$cext", "-C", work.resolve("control").absolutePath, ".") != 0) return@doLast
            work.listFiles { f -> f.name == "debian-binary" }?.forEach { it.delete() }
            File(work, "debian-binary").writeText("2.0\n")
            deb.delete()
            if (exec("ar", "rc", deb.absolutePath, "debian-binary", "control.tar$cext", "data.tar$ext") != 0) {
                println("no se pudo reconstruir el .deb")
            } else {
                println("relanzador listo: ${deb.name}")
            }
        }
    }
}
