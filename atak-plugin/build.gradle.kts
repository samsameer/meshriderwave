/**
 * MeshRider Wave ATAK Plugin
 *
 * Integrates MR Wave PTT with ATAK (Android Tactical Assault Kit)
 * Provides PTT button on ATAK toolbar and CoT synchronization
 *
 * ATAK SDK Setup:
 * 1. Download ATAK SDK from https://tak.gov (requires .mil/.gov email)
 * 2. Extract to /path/to/atak-sdk
 * 3. Set ATAK_SDK environment variable or update sdkPath below
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ATAK SDK path - update this or set ATAK_SDK environment variable
val atakSdkPath: String = System.getenv("ATAK_SDK") ?: "${rootDir}/../atak-sdk"

android {
    namespace = "com.doodlelabs.meshriderwave.atak"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.doodlelabs.meshriderwave.atak"
        minSdk = 26
        targetSdk = 34  // ATAK typically targets slightly older SDK
        versionCode = 1
        versionName = "1.0.0"

        // ATAK plugin metadata
        manifestPlaceholders["ATAK_MIN_VERSION"] = "4.10.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // ATAK requires specific packaging options
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // Lint configuration
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // ATAK SDK - Uncomment when SDK is available
    // implementation(files("$atakSdkPath/main.jar"))
    // implementation(files("$atakSdkPath/pluginsdk.aar"))

    // For development without ATAK SDK, use stubs
    compileOnly(project(":atak-plugin:atak-stubs"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // For CoT XML parsing
    implementation("com.google.code.gson:gson:2.11.0")
}

// Task to generate plugin APK with proper naming for ATAK
tasks.register<Copy>("packageAtakPlugin") {
    dependsOn("assembleRelease")
    from("${buildDir}/outputs/apk/release/")
    into("${rootDir}/atak-plugins/")
    include("*.apk")
    rename { "MRWave-ATAK-Plugin-${android.defaultConfig.versionName}.apk" }
}
