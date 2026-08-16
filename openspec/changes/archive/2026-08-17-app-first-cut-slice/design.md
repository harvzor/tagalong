## Context

See proposal.md — Why. The `:engine` module already exposes everything this slice needs from `engine/src/main`: `FfmpegCutEngine.losslessCut(File, startMs, durationMs, File)`, `CutMode`/`cut`, `MetadataReader.probe(File)`, and `DateTakenStore.registerAndReadBack(context, File, captureMillis)`. All are `File`-based and were stabilized in the preceding relocation change. `:cutdebug` is frozen reference and is not touched.

The behavioral contract for the cut itself lives in `openspec/specs/metadata-preserving-cut/spec.md` and is unchanged. This design is about the *app* that wires a user around that contract for the first time, and the two integration seams that have never run outside an instrumented test: `content://` Uri → `File` (input) and `File` → gallery (output).

## Goals / Non-Goals

**Goals:**
- Stand up the first `:app` module and prove both seams end to end on a device.
- Reuse `:engine` as-is for the happy path; keep the engine's `File` contract untouched.
- A real, usable trim UX (preview + range slider) — the product's core surface, not a stub.
- Make success legible: show the read-back gallery date so the metadata guarantee is visible.

**Non-Goals (design-level):**
- No abstraction layer or DI framework — this is one screen; a `ViewModel` plus the engine objects is enough.
- No re-encode path or mode plumbing (step 2). No keyframe-snap messaging (step 3). No filmstrip (later).
- No support below API 29's scoped-storage gallery mechanism — minSdk 31 sidesteps the legacy `DATA`/`WRITE_EXTERNAL_STORAGE` path entirely.

## Decisions

### D1 — Materialize the picked Uri to cache; keep the engine File-based

The picker returns a `content://` Uri the app doesn't own and can't resolve to a path; ffmpeg/ffprobe need a real path. Copy the Uri to `cacheDir/input.<ext>` via `ContentResolver.openInputStream` before handing the `File` to the engine.

- **Why over the alternative:** ffmpeg-kit offers `FFmpegKitConfig.getSafParameterForRead` (a `saf:` path with no copy), but `-ss` seeking over a non-seekable SAF stream is unproven and would be the riskiest thing in the *first* APK. Copy-to-cache works on every device/format, keeps the engine contract identical to what the contract test already exercises, and isolates the seam to one well-understood step. The cost is one full-file copy — acceptable for step 1; `saf:` remains a later optimization with no rework to the engine.
- The same cached `File` is the single source of truth for preview, duration, cut input, and probing — one materialization, several readers.

### D2 — ExoPlayer previews; ffmpeg cuts (two tools, two jobs)

The preview uses Media3 ExoPlayer via `AndroidView(PlayerView)` inside Compose. It plays the cached `File` and seeks on trim-handle drag.

- **This does not resurrect the Media3 cut engine** that lost the bake-off — that was Media3 **Transformer** as a *cutter*. Here ExoPlayer is only a *preview player*; the cut is still `FfmpegCutEngine.losslessCut`. Keeping them separate avoids reopening a settled decision.
- ExoPlayer honours rotation metadata, so portrait clips preview upright with no extra work — consistent with the engine's "orientation is a signal" guarantee.
- Player lifecycle is the one fiddly part: `remember` the player, `release()` on dispose, pause when backgrounded.

### D3 — Trim range: Material3 `RangeSlider` over probed duration; live seek

Duration comes from `MediaMetadataRetriever.METADATA_KEY_DURATION` on the cached file (kept in `:app`, so `:engine` needs no new probe surface). The `RangeSlider` spans `0..durationMs`; state holds `startMs`/`endMs`; the cut is `losslessCut(file, startMs, endMs - startMs, output)`.

- Dragging seeks the preview to the handle being moved (diff the range to detect which thumb changed; debounce seeks while dragging).
- **Filmstrip deferred with no rework:** the slider and its `0..duration` coordinate space are the same whether or not thumbnails sit behind it. A filmstrip later becomes a track background over this exact slider, so nothing here is thrown away.

### D4 — Parameterize `DateTakenStore` output location

`DateTakenStore.registerAndReadBack` currently hardcodes `RELATIVE_PATH = "Movies/CutDebug"` and a `nanoTime`-based display name — fine for a test, wrong for a real save. Add parameters for relative path (default `Movies/Tagalong`) and display name.

- **Why now:** this is the first *real* use of that code; the gallery folder and filename are user-visible. The change is additive and does not alter the gallery-date guarantee, so `metadata-preserving-cut` is unaffected and `:cutdebug`'s frozen copy is irrelevant.

### D5 — Stack: Compose, minSdk 31, `:app`-scoped version catalog

Mirror `android-bluetooth-bouncer` (the maintained modern app): Compose/Material3, AGP 9.2 / Kotlin 2.3, Compose BOM, `activity-compose`, lifecycle, coroutines, plus Media3. `minSdk = 31`, `compileSdk = 36`.

- Introduce `gradle/libs.versions.toml` wiring **only** `:app`. `:engine` and `:cutdebug` keep their current inline `id(...) version` style — no churn to the frozen/relocated modules.
- minSdk 31 (vs the API-29 floor `DateTakenStore` needs) matches bouncer and keeps headroom.

### D6 — Threading and error surfacing

Run copy → probe → `losslessCut` → `DateTakenStore` on `Dispatchers.IO` from the `ViewModel`; the screen renders idle / working / saved(date) / error states. `losslessCut` and `DateTakenStore` throw on failure — catch and route to the error state. This satisfies "never silently degrade" at the app boundary, not just in the engine.

## Risks / Trade-offs

- **[ExoPlayer lifecycle leaks in Compose]** → `release()` in `DisposableEffect`/`onDispose`; pause on lifecycle stop. Standard pattern, but the most likely source of a bug in this slice.
- **[Large-file copy latency before preview]** → the cache copy runs off the main thread with a visible working state; acceptable for step 1. If it becomes painful, `saf:` (D1 alternative) is the escape hatch, engine unchanged.
- **[First run against arbitrary user videos, not the one bundled fixture]** → codecs/containers/tags the contract test never saw may surface. This is the *point* of the slice — failures are surfaced (D6), and any engine gap found here is real signal, not app breakage.
- **[Trim handle → which-thumb-moved detection]** → `RangeSlider` reports the whole range; diff against previous state to pick the seek target. Minor, contained to the slider binding.
- **[minSdk 31 excludes API 26–30 devices]** → deliberate for this slice; the legacy gallery path is out of scope and can be revisited if a lower floor is ever required.
