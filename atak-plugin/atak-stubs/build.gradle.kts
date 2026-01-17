/**
 * ATAK SDK Stubs
 *
 * Provides compile-time stubs for ATAK SDK interfaces and classes.
 * This allows the plugin to compile without the proprietary ATAK SDK.
 * At runtime, the real ATAK SDK classes will be used.
 *
 * When you have the real ATAK SDK:
 * 1. Comment out the compileOnly dependency on this module
 * 2. Add the real SDK jars as dependencies
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atakmap.stubs"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
