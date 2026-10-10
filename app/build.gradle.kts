import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "io.github.menadion.magus"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.menadion.magus"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.8"
    }

    // Release signing: keystore.properties (git-ignored) points at the key outside the repo.
    val keystoreProps = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.inputStream().use { keystoreProps.load(it) }
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8, on since 2026-10-10. Before this the release carried every class and resource of
            // Compose, Firebase and MapLibre, used or not: 12.5 MB of the 35.5 MB was code Mogar
            // never calls. Rules in proguard-rules.pro, which is short on purpose; the libraries
            // ship their own and R8 applies those itself.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Both ARM builds. 64-bit covers most phones, but a budget Samsung (the Galaxy A13)
            // runs a 32-bit Android on 64-bit silicon and refuses a 64-bit-only package outright.
            // The emulator uses debug, which carries every ABI.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Debug carries the same key, so a debug build can update a release one in place (the
        // emulator keeps its family) and the other way round.
        debug {
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("org.maplibre.gl:android-sdk:13.6.1")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // The daily update check, which has to run on a phone nobody opens (see AutoUpdate).
    implementation("androidx.work:work-runtime-ktx:2.11.0")
}
