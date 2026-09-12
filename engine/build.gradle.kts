plugins {
    id("com.android.library")
}

android {
    namespace = "dev.tagalong.engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            // Deliberate phone-heap simulation (~256 MB, the stock per-app Java heap on
            // most devices; the app declares no largeHeap). Mp4LocationHeapBudgetTest
            // proves the container-metadata stages complete on video files larger than
            // this budget. The pin is module-wide because :engine has no other unit
            // tests; re-scope it to a dedicated Test task if future unit tests need
            // more room. Do not raise it to make a whole-file read "pass".
            it.maxHeapSize = "256m"
            it.systemProperty("tagalong.sampleVideos", rootProject.file("sample-videos").absolutePath)
        }
    }
}

dependencies {
    // Winner of the cut-engine bake-off (cutdebug/notes/results.md). Production dependency:
    // this module ships the engine, unlike cutdebug where it was test-only.
    implementation("com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
