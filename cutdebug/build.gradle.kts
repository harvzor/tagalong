plugins {
    id("com.android.library")
}

android {
    namespace = "dev.tagalong.cutdebug"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Winner of the cut-engine bake-off (notes/results.md).
    androidTestImplementation("com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0")

    // Kept side-by-side for reference/debugging even though it lost the bake-off — see
    // notes/results.md and Media3CutEngine's doc comment.
    androidTestImplementation("androidx.media3:media3-transformer:1.8.0")
    androidTestImplementation("androidx.media3:media3-muxer:1.8.0")
    androidTestImplementation("androidx.media3:media3-common:1.8.0")
    androidTestImplementation("androidx.media3:media3-effect:1.8.0")
}
