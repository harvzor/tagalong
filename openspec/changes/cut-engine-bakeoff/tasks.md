## 1. Spike module & fixtures

- [ ] 1.1 Create a minimal Android module for the spike (no app UI; instrumented-test-only) targeting a device/emulator over adb
- [ ] 1.2 Add the primary fixture `xiaomi-poco-x5.mp4` as a test asset and record its baseline tags via ffprobe (creation_time, location, com.android.* / com.xiaomi.* camera tags, rotation=-90, codecs)
- [ ] 1.3 Confirm the spike module's Gradle toolchain builds; record any versions it forces (watch for an ffmpeg-kit AAR clash)

## 2. Shared test harness

- [ ] 2.1 Define a `CutEngine` interface with `losslessCut(...)` and `reencodeCut(...)` returning an output file
- [ ] 2.2 Implement a metadata reader that extracts container tags on-device (MediaExtractor/MediaMetadataRetriever) for a neutral, engine-independent cross-check
- [ ] 2.3 Implement the assertion `source_tags ⊆ output_tags` by key and value (added tags allowed; no source tag lost) — spec: "Lossless cut preserves all source file-level metadata"
- [ ] 2.4 Implement a `MediaStore.DATE_TAKEN` writer (set to source capture date) and reader (post media-scan) — spec: "Gallery date is preserved"
- [ ] 2.5 Implement rotation and original-file-untouched assertions — specs: "Orientation is preserved as a signal", "The original file is never modified"

## 3. Arm A — Media3 Transformer

- [ ] 3.1 Add `androidx.media3:media3-transformer` + `media3-muxer`; implement `CutEngine` via transmux (lossless) and transcode (re-encode)
- [ ] 3.2 Run the full assertion set for lossless mode against the fixture; capture pass/fail per tag (esp. com.android.* / com.xiaomi.*)
- [ ] 3.3 Run the full assertion set for re-encode mode; verify the guarantee holds identically — spec: "The guarantee holds identically in both modes"
- [ ] 3.4 Record which tags (if any) Media3 drops, and whether DATE_TAKEN is preserved

## 4. Arm B — antonkarpenko ffmpeg-kit

- [ ] 4.1 Add `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`; implement `CutEngine` using the validated command (`-c copy -map_metadata 0 -movflags +faststart+use_metadata_tags` for lossless; re-encode variant for the other mode)
- [ ] 4.2 Run the full assertion set for lossless mode against the fixture
- [ ] 4.3 Run the full assertion set for re-encode mode
- [ ] 4.4 Record tag results, DATE_TAKEN result, and note binary size / GPL implication for the decision

## 5. Decision & handoff

- [ ] 5.1 Produce a results table: each engine × mode × requirement (tags, DATE_TAKEN, rotation, source-untouched), plus baggage (size, license, maintenance)
- [ ] 5.2 Pick the winner: the engine passing the `metadata-preserving-cut` contract with the least baggage; if only ffmpeg passes, that decides it
- [ ] 5.3 If neither engine preserves DATE_TAKEN, flag it as a product-scope finding to resolve before any app code (per design Risk #2)
- [ ] 5.4 Record the decision (winning engine, note ffmpeg-kit-next as the ffmpeg arm's future migration target) to feed the environment-setup and app-build changes; delete the losing arm
