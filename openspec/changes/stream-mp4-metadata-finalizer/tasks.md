## 1. Failing regression tests first

- [ ] 1.1 Add `engine/src/test` unit-test source set wiring in `engine/build.gradle.kts`: `testImplementation("junit:junit:4.13.2")` and `testOptions.unitTests.all { maxHeapSize = "256m" }` with a comment explaining the phone-heap simulation (module-wide pin is deliberate; this module has no other unit tests).
- [ ] 1.2 Add a test-only `free`-padding fixture helper that copies a corpus sample (read from `rootProject.file("sample-videos")`) and appends valid root-level `free` boxes to reach a requested size; JUnit `TemporaryFolder` cleanup; no large binaries committed.
- [ ] 1.3 Add `Mp4LocationHeapBudgetTest`: `preserve()` against a padded source >300 MB and padded output, asserting success plus `quickTimePayloadsEqual` and valid `stco`/`co64` offsets in the result; `inspect(File)` against a padded file larger than the heap budget, asserting the correct representation is returned.
- [ ] 1.4 Run `./gradlew :engine:testDebugUnitTest` on current code and record the expected failure (`OutOfMemoryError` at `Mp4LocationFinalizer.preserve`) in the PR/commit notes; the suite must contain no other failures.

## 2. Streaming container-metadata implementation

- [ ] 2.1 Rework `Mp4LocationMetadata.inspect(File)` to a seekable `RandomAccessFile` box walk returning the same `LocationRepresentationInfo`; read only `©xyz` payload and `keys` entry bytes; keep the `inspect(ByteArray)` overload and reuse the existing box grammar verbatim (headers, `meta` child-start heuristic, container set, `©` handling).
- [ ] 2.2 Convert file positions/sizes in the walker and finalizer to `Long`; restrict `ByteArray` allocation to individual metadata atoms behind an explicit small-size guard; remove every `Int` conversion of file-scale offsets so no 2 GB ceiling remains.
- [ ] 2.3 Rework `Mp4LocationFinalizer.preserve` to the D1 two-phase rewrite: seekable descriptor pass → stream-splice copy output→temp through `FileChannel` (loop on partial `transferTo`) with raw source `©xyz` bytes inserted at the splice point → in-place fixed-width patch pass on the temp file for `udta`/`moov` sizes and `stco`/`co64` entries. Preserve all current fail-loud guards (fragments, multiple `moov`/`udta`, non-contiguous `©xyz`, chunk pointing into rewritten range, overflow cases) and the no-rewrite fast path when the output already matches.
- [ ] 2.4 Move self-verification to re-inspect the temp file seekably before install; keep temp-file-and-swap installation and the existing exception types so `FfmpegCutEngine.executeAndFinalize` needs no change.

## 3. Equivalence verification

- [ ] 3.1 Run `./gradlew :engine:testDebugUnitTest`: heap-budget tests now green, all existing unit-style checks green.
- [ ] 3.2 Run `./gradlew :engine:connectedAndroidTest` on `Pixel_7_API_34` unchanged: corpus `©xyz` payload equality, vendor tags, `creation_time`, gallery date, source immutability, MediaExtractor readability (re-encode rotation failure remains the only known documented failure).
- [ ] 3.3 Run `./gradlew :app:connectedAndroidTest`: `E2eCutTest` representation assertions pass end-to-end through the MediaStore path.

## 4. Documentation and release checklist

- [ ] 4.1 Add a line to the release manual-verification practice (next to or inside the existing manual-verification documentation pattern): cut a multi-minute 4K clip on the real Pixel 10a and confirm success, `©xyz` payload preservation, and gallery playback before release.
- [ ] 4.2 Note in the engine code (class KDoc on the finalizer) that unsupported-layout and fail-loud semantics are unchanged by the streaming rework, and that the design removes the former whole-file buffering and 2 GB ceiling.
- [ ] 4.3 Run `openspec validate --change "stream-mp4-metadata-finalizer"`; after on-device verification, proceed with the normal archive flow so the `metadata-preserving-cut` requirement lands in the main spec.
