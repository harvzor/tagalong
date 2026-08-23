plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

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
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
