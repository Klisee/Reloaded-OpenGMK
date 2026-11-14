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
        }
        windows {
            iconFile.set(project.file("icons/reloaded-opengmk.ico"))
        }
    }
}
