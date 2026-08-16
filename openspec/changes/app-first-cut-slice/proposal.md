## Why

Everything built so far is a library plus instrumented tests: `:engine` holds the proven cut engine and `:cutdebug` is the frozen bake-off harness. No APK exists, and the two scary integration seams — turning a picked `content://` Uri into a real file the engine can read, and writing the output back into the gallery under its original capture date — have never run outside a test. This change stands up the first `:app` module and drives those seams end to end for the first time, using only the LOSSLESS engine mode that already passes the contract test. It is step 1 of the agreed four-step path to a shippable app.

## What Changes

- Add a new `:app` module (`com.android.application`, Jetpack Compose, minSdk 31) that depends on `:engine`. `:cutdebug` stays frozen and untouched.
- A single Compose screen lets the user: pick a video, preview it with a scrubbable trim range, and run a lossless cut that is saved to the gallery.
- **Input seam**: the picked `content://` Uri is materialized to the app cache as a `File` before it reaches the engine, so the engine's existing `File`-based contract is not touched.
- **Trim UX**: a Media3 ExoPlayer preview of the picked video plus a Material3 `RangeSlider` over the clip duration; dragging a handle seeks the preview. (ExoPlayer is used only to *show* frames — ffmpeg still performs the cut.)
- **Output seam**: the cut output is registered via `DateTakenStore` so it appears in the gallery under the source's original capture date, and the read-back date is surfaced to the user as visible proof.
- The cut runs off the main thread, and any failure is surfaced to the user rather than swallowed (per "never silently degrade").
- Small `:engine` change: parameterize `DateTakenStore`'s gallery relative path and output display name (currently hardcoded to the test-only `Movies/CutDebug` + a `nanoTime` name) so a real save lands in a sane location. No behavioral change to the gallery-date guarantee.
- Introduce a `libs.versions.toml` version catalog wiring only the new `:app` module; `:engine` and `:cutdebug` keep their current inline versions.

Explicitly out of scope for this slice (deferred by the agreed plan): the lossless/re-encode mode toggle and re-encode path (step 2); surfacing the keyframe-snap caveat and richer error/polish (step 3); a filmstrip thumbnail track under the slider (later — adds no rework to the slider built here).

## Capabilities

### New Capabilities
- `cut-workflow`: the user-facing flow that turns a picked video into a saved, metadata-correct cut — picking a source, previewing and choosing a trim range, running a lossless cut, and saving the result to the gallery under the original capture date, with failures surfaced. Later steps extend this same capability (mode toggle, caveat messaging).

### Modified Capabilities
<!-- None. The engine's behavioral contract in openspec/specs/metadata-preserving-cut/spec.md is unchanged: this slice consumes it. Parameterizing DateTakenStore's output location is an implementation detail, not a spec-level behavior change. -->

## Impact

- **New module** `:app` — Compose/Material3, `activity-compose`, lifecycle, coroutines, and a new Media3 ExoPlayer dependency (preview only). `settings.gradle.kts` gains `include(":app")`.
- **New** `gradle/libs.versions.toml` — version catalog scoped to `:app` (bouncer-style: AGP 9.2 / Kotlin 2.3, Compose BOM).
- **`:engine`** — `DateTakenStore.registerAndReadBack` gains parameters for gallery relative path and display name; the existing gallery-date guarantee is unchanged. `CutEngine`/`FfmpegCutEngine`/`MetadataReader`/`CutMode` are consumed as-is.
- **`:cutdebug`** — unchanged (frozen reference).
- **Permissions** — none required for the modern flow: `PickVisualMedia` needs no storage permission, and the MediaStore pending-insert write targets a row the app itself creates (API 29+; minSdk 31 clears this comfortably).
- **New user-facing surface** — the first installable APK; also the first place the engine runs against arbitrary user-picked videos rather than the single bundled test fixture.
