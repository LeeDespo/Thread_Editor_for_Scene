// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.scenepreset.editor"

kotlin {
    android {
        androidResources.enable = true
        buildToolsVersion = "37.0.0"
        compileSdk {
            version = release(37) { minorApiLevel = 0 }
        }
        minSdk = 24
        namespace = "com.scenepreset.editor.shared"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.miuix.ui)
                implementation(libs.miuix.icons)
                implementation(libs.miuix.preference)
                implementation(libs.miuix.nav)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.navigationevent.compose)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

compose.resources {
    publicResClass = true
}
