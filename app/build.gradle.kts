plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the shipped architecture set. Feeds BOTH ndk.abiFilters below
// (which bounds the merged native-lib set, and therefore what the universal APK can contain)
// and splits.abi.include further down (which decides which per-architecture splits are
// generated). One declaration, so the two cannot drift apart.
//
// The 32-bit ABIs (armeabi-v7a, x86) are deliberately absent: unreachable at minSdk 31. They
// are excluded from every artifact, including the universal one -- ndk.abiFilters is what makes
// that exclusion complete, because splits.abi.include() alone does NOT bound the universal APK.
// Measured on AGP 9.5: include() by itself left the universal APK merging all four ABIs from
// the ffmpeg-kit AAR, 243 MB with 32-bit intact. See openspec: split-release-apks-by-abi.
val shippedAbis = listOf("arm64-v8a", "x86_64")

android {
    namespace = "dev.tagalong.app"
    compileSdk = 36

    signingConfigs {
        val keystorePath = findProperty("releaseKeystorePath")?.toString()
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = findProperty("releaseStorePassword")?.toString()
                keyAlias = findProperty("releaseKeyAlias")?.toString()
                keyPassword = findProperty("releaseKeyPassword")?.toString()
            }
        }
    }

    defaultConfig {
        applicationId = "dev.tagalong.app"
        minSdk = 31
        targetSdk = 36
        val appVersion = findProperty("appVersionName")?.toString()?.takeIf { it.isNotBlank() }
        // Strip any pre-release suffix (e.g. "-rc1", "-alpha") before parsing numeric parts
        val baseVersion = appVersion?.substringBefore("-")
        val parsedParts = baseVersion?.split(".")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }
        versionName = if (parsedParts != null) appVersion!! else "0.0.0-dev"
        versionCode = if (parsedParts != null) parsedParts[0] * 10_000 + parsedParts[1] * 100 + parsedParts[2] else 1

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bounds which architectures enter the merged native-lib set, and so what the combined
        // universal APK can contain. Required: splits.abi.include() governs only which splits
        // are generated. Fed from shippedAbis so there is one list, not two to keep in sync.
        ndk {
            abiFilters += shippedAbis
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Off by decision, not oversight: saves ~24 MB/artifact, declined as unverifiable
            // rather than as too small. Read the record before enabling.
            // openspec/changes/archive/2026-09-30-enable-code-shrinking/notes/apply-measurements.md
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir(rootProject.file("sample-videos"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Ship one APK per supported architecture plus a combined "universal" APK, rather than a
    // single APK carrying every ABI the ffmpeg-kit-full-gpl AAR bundles. `reset()` is required:
    // without it the AGP default ABI list is kept alongside the explicit include(). Note that
    // include() here decides which splits exist; the architectures themselves are bounded by
    // ndk.abiFilters in defaultConfig, both fed from shippedAbis above.
    splits {
        abi {
            isEnable = true
            reset()
            include(*shippedAbis.toTypedArray())
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.core)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Espresso 3.6.1 uses the removed InputManager.getInstance() API on Android 17/API 37.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}
