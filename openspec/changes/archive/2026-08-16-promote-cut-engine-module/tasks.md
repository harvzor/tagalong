## 1. Module scaffold

- [x] 1.1 Create `engine/` module directory with `engine/build.gradle.kts` as a `com.android.library`, namespace `dev.tagalong.engine`, mirroring `cutdebug`'s toolchain (compileSdk 36, minSdk 26, Java 17, built-in Kotlin — no `org.jetbrains.kotlin.android` plugin, no `kotlinOptions {}` block, `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, and the `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }` block).
- [x] 1.2 In `engine/build.gradle.kts`, declare `ffmpeg-kit-full-gpl:2.1.0` as a production `implementation` dependency, and the androidx.test deps (`junit`, `runner`, `rules`, `espresso-core`) as `androidTestImplementation`. Do NOT add any media3 dependency.
- [x] 1.3 Add `engine/src/main/AndroidManifest.xml` (minimal, matching `cutdebug`'s).
- [x] 1.4 Register the module: add `include(":engine")` to `settings.gradle.kts`.

## 2. Copy production engine into `engine/src/main`

- [x] 2.1 Copy `CutEngine.kt` (the `CutEngine` interface, `CutMode` enum, and `cut()` extension) into `engine/src/main/java/dev/tagalong/engine/`, changing only the `package` declaration to `dev.tagalong.engine`.
- [x] 2.2 Copy `FfmpegCutEngine.kt` (including its re-encode rotation-gap doc comment, verbatim) into `src/main`, changing only the package. Do not touch the ffmpeg command logic.
- [x] 2.3 Copy `MetadataReader.kt` (with `MediaProbe`) into `src/main`, changing only the package.
- [x] 2.4 Copy `DateTakenStore.kt` into `src/main`, changing only the package.
- [x] 2.5 Do NOT copy `Media3CutEngine.kt` (stays frozen in `cutdebug`).

## 3. Copy contract test into `engine/src/androidTest`

- [x] 3.1 Copy `CutEngineContractTest.kt`, `FfmpegCutEngineTest.kt`, `MetadataAssertions.kt`, `FileAssertions.kt`, and `TestFixtures.kt` into `engine/src/androidTest/java/dev/tagalong/engine/`, changing only the `package` declarations to `dev.tagalong.engine`.
- [x] 3.2 Copy the fixture asset `xiaomi-poco-x5.mp4` into `engine/src/androidTest/assets/`.
- [x] 3.3 Do NOT copy `Media3CutEngineTest.kt`.
- [x] 3.4 Confirm `FfmpegCutEngineTest` still wires `engine() = FfmpegCutEngine()` against the `src/main` engine (no other engine subclass exists in `:engine`).

## 4. Build and verify on device

- [x] 4.1 Build the module and its test APK: `./gradlew.bat :engine:assembleDebug :engine:assembleDebugAndroidTest` — confirm both compile clean with ffmpeg-kit as a `main` dependency.
- [x] 4.2 Boot the `Pixel_7_API_34` AVD and confirm `adb devices` shows it online.
- [x] 4.3 Run `./gradlew.bat :engine:connectedAndroidTest`. Expected result: LOSSLESS passes; REENCODE fails on the rotation-signal assertion — the documented, carried-forward gap, NOT a port regression.

## 5. Confirm freeze and record state

- [x] 5.1 Verify `cutdebug/` is byte-for-byte unchanged (`git status` shows no modifications under `cutdebug/`); only `engine/` and `settings.gradle.kts` are new/changed.
- [x] 5.2 Note in the change's completion that the engine now ships from `:engine` guarded by its own contract test, `cutdebug` remains the frozen reference, and the ffmpeg-kit GPL licensing decision (design Open Questions) is still open and must be resolved before an app ships.
