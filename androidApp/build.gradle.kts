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


android {
    namespace = "com.klisee.reloaded.gmk"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.klisee.reloaded"
        minSdk = 26
        targetSdk = 36
        versionCode = 67
        versionName = "0.6.7"
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

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.material3)
    testImplementation("junit:junit:4.13.2")
}
