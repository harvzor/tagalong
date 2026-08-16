## 1. Spike module & fixtures

- [x] 1.1 Create a minimal Android module for the spike (no app UI; instrumented-test-only) targeting a device/emulator over adb
- [x] 1.2 Add the primary fixture `xiaomi-poco-x5.mp4` as a test asset and record its baseline tags via ffprobe (creation_time, location, com.android.* / com.xiaomi.* camera tags, rotation=-90, codecs)
- [x] 1.3 Confirm the spike module's Gradle toolchain builds; record any versions it forces (watch for an ffmpeg-kit AAR clash)

## 2. Shared test harness

- [x] 2.1 Define a `CutEngine` interface with `losslessCut(...)` and `reencodeCut(...)` returning an output file
- [x] 2.2 Implement a metadata reader that extracts container tags on-device (MediaExtractor/MediaMetadataRetriever) for a neutral, engine-independent cross-check
- [x] 2.3 Implement the assertion `source_tags ⊆ output_tags` by key and value (added tags allowed; no source tag lost) — spec: "Lossless cut preserves all source file-level metadata"
- [x] 2.4 Implement a `MediaStore.DATE_TAKEN` writer (set to source capture date) and reader (post media-scan) — spec: "Gallery date is preserved"
- [x] 2.5 Implement rotation and original-file-untouched assertions — specs: "Orientation is preserved as a signal", "The original file is never modified"

## 3. Arm A — Media3 Transformer

- [x] 3.1 Add `androidx.media3:media3-transformer` + `media3-muxer`; implement `CutEngine` via transmux (lossless) and transcode (re-encode)
- [x] 3.2 Run the full assertion set for lossless mode against the fixture; capture pass/fail per tag (esp. com.android.* / com.xiaomi.*)
- [x] 3.3 Run the full assertion set for re-encode mode; verify the guarantee holds identically — spec: "The guarantee holds identically in both modes"
- [x] 3.4 Record which tags (if any) Media3 drops, and whether DATE_TAKEN is preserved

  **Result: Media3 fails both modes.** Drops all three manufacturer tags
  (`com.android.manufacturer`, `com.android.model`, `com.xiaomi.product.marketname`) and
  overwrites `creation_time` with the export timestamp, in both lossless and re-encode mode.
  `location`/`location-eng` survive. DATE_TAKEN was never reached (fails earlier in the
  assertion chain). Full detail: notes/results.md. Arm deleted per task 5.4.

## 4. Arm B — antonkarpenko ffmpeg-kit

- [x] 4.1 Add `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`; implement `CutEngine` using the validated command (`-c copy -map_metadata 0 -movflags +faststart+use_metadata_tags` for lossless; re-encode variant for the other mode)
- [x] 4.2 Run the full assertion set for lossless mode against the fixture

  **Result: full pass.** All source tags (incl. vendor tags), `creation_time`, `location`,
  rotation, dimensions, source-untouched, and `DATE_TAKEN` all verified on-device.

- [x] 4.3 Run the full assertion set for re-encode mode

  **Result: pass except rotation signal.** Tags/`creation_time`/`location`/dimensions/
  source-untouched/`DATE_TAKEN` all pass. Rotation signal is lost (pixels are correctly
  *not* baked-in thanks to `-noautorotate`, but no CLI-only re-stamp path exists on this
  ffmpeg build — see notes/rotation-reencode-gap.md for everything tried). Carried forward
  as a scoped follow-up, not a blocker to the decision (task 5).

- [x] 4.4 Record tag results, DATE_TAKEN result, and note binary size / GPL implication for the decision — see notes/results.md

## 5. Decision & handoff

- [x] 5.1 Produce a results table: each engine × mode × requirement (tags, DATE_TAKEN, rotation, source-untouched), plus baggage (size, license, maintenance) — see notes/results.md
- [x] 5.2 Pick the winner: the engine passing the `metadata-preserving-cut` contract with the least baggage; if only ffmpeg passes, that decides it

  **Winner: ffmpeg-kit** (`com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`). Media3 Transformer
  fails the core "no source tag lost" requirement in both modes; ffmpeg-kit passes lossless
  cleanly and re-encode except for one scoped, documented gap (rotation signal).

- [x] 5.3 If neither engine preserves DATE_TAKEN, flag it as a product-scope finding to resolve before any app code (per design Risk #2)

  **Not triggered** — ffmpeg-kit preserves `DATE_TAKEN` in both modes.

- [x] 5.4 Record the decision (winning engine, note ffmpeg-kit-next as the ffmpeg arm's future migration target) to feed the environment-setup and app-build changes; delete the losing arm

  Decision and follow-ups recorded in notes/results.md and notes/rotation-reencode-gap.md.
  Module later renamed `cutspike` → `cutdebug` to reflect its ongoing role as an on-device
  debugging/verification harness rather than disposable spike code — and per that same
  reframing, **the Media3 arm was kept rather than deleted** (deviates from this task's
  literal "delete the losing arm," by explicit request): `Media3CutEngine.kt` and
  `Media3CutEngineTest.kt` are still present, `media3-*` dependencies still declared in
  `cutdebug/build.gradle.kts`, so both engines remain runnable side-by-side for future
  debugging/comparison. Media3's tests are expected to fail — that failure *is* the
  documented finding, not a regression.
