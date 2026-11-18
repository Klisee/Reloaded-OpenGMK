import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val keystorePropertiesFile = rootProject.file("androidApp/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}

// Un APK por ABI con -PtargetAbi=<abi>. Por defecto solo arm64-v8a, que es lo
// que release.yml espera (un unico APK con la lib arm64); universal empaqueta
// las tres y se pide explicitamente:
//   ./gradlew :androidApp:assembleRelease                         (arm64-v8a)
//   ./gradlew :androidApp:assembleRelease -PtargetAbi=armeabi-v7a
//   ./gradlew :androidApp:assembleRelease -PtargetAbi=x86_64
//   ./gradlew :androidApp:assembleRelease -PtargetAbi=universal
// El motor de cada ABI debe estar ya en src/main/jniLibs/<abi>/libgm8emulator.so;
// build-android.yml lo compila con cargo-ndk antes de llamar a Gradle.
// No se usan splits abi: release.yml espera un unico APK en outputs y lo renombra.
val allAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val targetAbi = providers.gradleProperty("targetAbi").orElse("arm64-v8a").get()
require(targetAbi == "universal" || targetAbi in allAbis) {
    "ABI no soportada: $targetAbi (valores: universal, ${allAbis.joinToString()})"
}
val buildAbis = if (targetAbi == "universal") allAbis else listOf(targetAbi)

// Gradle no compila Rust: el .so lo deja build-android.yml (o tools/) antes.
// Fallar aqui es mejor que empaquetar un APK sin motor que revienta al abrirlo.
val checkEmulatorSo by tasks.registering {
    val abis = buildAbis
    val jniLibs = layout.projectDirectory.dir("src/main/jniLibs")
    doLast {
        abis.forEach { abi ->
            val so = jniLibs.file("$abi/libgm8emulator.so").asFile
            require(so.isFile) {
                "falta ${so.path}; compilalo con: cargo ndk -t $abi --platform 29 " +
                    "build -p gm8emulator --release --features backend-wgpu (desde engine/src/OpenGMK)"
            }
        }
    }
}

android {
    namespace = "com.klisee.reloaded.gmk"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.klisee.reloaded"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        ndk { abiFilters += buildAbis }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.isFile) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePropertiesFile.isFile) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(checkEmulatorSo) }

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.material3)
    testImplementation("junit:junit:4.13.2")
}
