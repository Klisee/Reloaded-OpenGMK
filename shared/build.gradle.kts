plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.klisee.reloaded.gmk.resources"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.klisee.reloaded.gmk.shared"
        compileSdk = 37
        minSdk = 26
    }

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        target.compilations.getByName("main").cinterops.create("reloadedios") {
            defFile(project.file("src/nativeInterop/cinterop/reloaded_ios.def"))
            includeDirs(project.file("src/nativeInterop/cinterop"))
        }
    }

    sourceSets {
        val commonMain by getting
        val jvmCommonMain by creating {
            dependsOn(commonMain)
        }
        getByName("androidMain").dependsOn(jvmCommonMain)
        getByName("androidMain").dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
            implementation("androidx.core:core-ktx:1.19.0")
        }
        getByName("desktopMain").dependsOn(jvmCommonMain)

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.5.0")
                implementation("io.ktor:ktor-client-darwin:3.1.0")
                implementation("io.github.vinceglb:filekit-dialogs-compose:0.17.0")
            }
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        commonMain.dependencies {
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("io.github.kyant0:backdrop:2.0.0")
            implementation("io.coil-kt.coil3:coil-compose:3.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }

        jvmCommonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("io.github.kyant0:backdrop:2.0.0")
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.coil-kt.coil3:coil-compose:3.5.0")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
        }
    }
}
