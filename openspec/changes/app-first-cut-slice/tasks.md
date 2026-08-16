## 1. Module + build setup

- [ ] 1.1 Add `gradle/libs.versions.toml` version catalog scoped to `:app` (AGP 9.2 / Kotlin 2.3, Compose BOM, activity-compose, lifecycle, coroutines, Media3 ExoPlayer + UI). Leave `:engine`/`:cutdebug` inline versions untouched.
- [ ] 1.2 Create the `:app` module (`com.android.application`, Compose enabled, minSdk 31, compileSdk 36, JVM 17) with `implementation(project(":engine"))`.
- [ ] 1.3 Add `include(":app")` to `settings.gradle.kts`; add an `AndroidManifest.xml` with a launcher `MainActivity` (no runtime permissions declared).
- [ ] 1.4 Confirm a clean Compose app skeleton builds and installs (empty screen), so later work debugs features, not scaffolding.

## 2. Input seam — pick and materialize (D1)

- [ ] 2.1 Wire `PickVisualMedia` (video-only) and expose the picked `content://` Uri to the screen state.
- [ ] 2.2 Copy the picked Uri to `cacheDir/input.<ext>` via `ContentResolver.openInputStream`, off the main thread; expose the resulting `File`. (Spec: user can select a source; original unmodified.)
- [ ] 2.3 Read clip duration from the cached file via `MediaMetadataRetriever.METADATA_KEY_DURATION`; expose `durationMs`.

## 3. Preview + trim UX (D2, D3)

- [ ] 3.1 Add an ExoPlayer preview (`AndroidView(PlayerView)`) playing the cached file; `remember`/`release` the player in a `DisposableEffect`, pause on lifecycle stop.
- [ ] 3.2 Add a Material3 `RangeSlider` over `0..durationMs` holding `startMs`/`endMs`, with `mm:ss.s` labels for start / end / length. (Spec: range bounded by clip; start cannot exceed end.)
- [ ] 3.3 On handle drag, detect which thumb moved and seek the preview to it; debounce seeks during drag. (Spec: adjusting a handle updates the preview.)

## 4. Cut + save wiring (D4, D6)

- [ ] 4.1 Parameterize `:engine` `DateTakenStore.registerAndReadBack` with `relativePath` (default `Movies/Tagalong`) and `displayName`; keep the gallery-date behavior unchanged.
- [ ] 4.2 In a `ViewModel`, run the pipeline on `Dispatchers.IO`: `MetadataReader.probe` → capture `creation_time` → `FfmpegCutEngine.losslessCut(file, startMs, endMs - startMs, output)` → `DateTakenStore.registerAndReadBack(...)`. (Spec: lossless cut of chosen range; source unmodified.)
- [ ] 4.3 Model screen state as idle / working / saved(galleryDate) / error, and render each. Show the read-back gallery date on success. (Spec: saved cut carries original date; app shows the applied date.)
- [ ] 4.4 Catch failures from copy / probe / cut / save and route them to the error state with a message; never present a failed cut as saved. (Spec: failures are surfaced, never silent.)

## 5. Verify the seams on-device

- [ ] 5.1 Run end to end on a device with a real gallery video: pick → trim → cut → save; confirm the output appears in the gallery under the source's original date (cross-check with the shown date and the gallery app).
- [ ] 5.2 Confirm the source file is unmodified after a cut (bytes/metadata intact) and that a forced failure (e.g. an unreadable pick) surfaces an error rather than a false success.
- [ ] 5.3 Spot-check a portrait clip previews and exports upright (rotation signal intact), and that at least one non-fixture codec/container from the device works or fails loudly.
