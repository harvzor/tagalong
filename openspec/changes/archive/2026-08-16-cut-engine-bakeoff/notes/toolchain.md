# Spike module toolchain

- **Gradle**: 9.5.0 (wrapper-pinned, `gradle/wrapper/gradle-wrapper.properties`)
- **AGP**: 9.2.1 (`com.android.library`)
- **Kotlin**: 2.3.20 — AGP 9's **built-in Kotlin support**; no `org.jetbrains.kotlin.android` plugin applied (AGP 9.0+ deprecates it — see https://developer.android.com/build/releases/agp-9-0-0-release-notes#android-gradle-plugin-built-in-kotlin). Also drops the `kotlinOptions {}` DSL block; `compileOptions.sourceCompatibility/targetCompatibility` alone is sufficient to drive the Kotlin JVM target under built-in Kotlin.
- **compileSdk**: 36, **minSdk**: 26, **Java**: 17 (source/target compatibility)
- **JDK**: Temurin 25.0.3 (host)

## Versions this toolchain forced

- Had to remove `org.jetbrains.kotlin.android` entirely — applying it under AGP 9.2.1 is now a hard build error ("no longer required... since AGP 9.0"), not just a warning.
- No AAR clash between `androidx.media3:*:1.8.0` and `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` — `assembleDebugAndroidTest` built clean with both dependencies present simultaneously. ffmpeg-kit's prebuilt native libs (`libavcodec.so`, `libavformat.so`, `libffmpegkit.so`, etc., armv7a/neon variants) packaged as-is (stripping skipped, expected for prebuilt `.so`s — not an error).
