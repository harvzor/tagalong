## Why

The `©xyz` location finalizer added in `f97fc44` reads the entire source file, the entire cut output, and a full rewrite copy into Java heap (`Mp4LocationFinalizer.preserve` → `File.readBytes()`; `Mp4LocationMetadata.inspect(File)` → `File.readBytes()`). On a stock app heap (~256 MB, no `largeHeap` is declared), this fails on realistically-sized footage: verified on host with the unmodified engine classes — a 300 MB source (a ~3-minute 4K clip) dies with `OutOfMemoryError` at `Mp4LocationFinalizer.kt:21` eleven milliseconds after ffmpeg finishes, and any file over 2 GB is structurally uncuttable regardless of heap because `ByteArray` is `Int`-indexed (verified: fails at an 8 GB heap with "File is too big to fit in memory"). Both entry points sit on the primary user path: `MetadataReader.probe` calls `inspect` on the picked file before any cut runs, so simply selecting a large video can exhaust the heap. Every other stage of the pipeline (`materializeToCache`, ffmpeg, `DateTakenStore`) already streams; this regression ships green because the test corpus is 11–16 MB.

## What Changes

- Rework `Mp4LocationMetadata.inspect(File)` to box-walk a seekable source (`RandomAccessFile`) instead of buffering the whole file; only the small `©xyz`/`keys` regions are read into memory.
- Rework `Mp4LocationFinalizer.preserve` to a streaming copy-through rewrite: read the output's box structure seekably, splice the raw source `©xyz` bytes while copying output → temp file through `FileChannel` transfer, patching box sizes and `stco`/`co64` offsets in the rewritten stream. No stage may buffer a whole file; the existing fail-loud semantics for unsupported layouts, self-verification before install, and temp-file-and-swap installation are retained unchanged.
- Remove the design's implicit 2 GB ceiling: no container-metadata stage may index file positions with `Int` where file-scale offsets occur.
- Add a host-side unit-test source set to `:engine` (none exists today) with a heap-budget regression test: unit-test JVM capped at 256 MB via `testOptions.unitTests.all { maxHeapSize }`, fixtures are real corpus samples grown with valid root-level `free` padding boxes at test time (no large binaries in git). Both `preserve` and `inspect(File)` run against fixtures larger than the heap; today these fail with `OutOfMemoryError`, after the change they pass.
- Existing payload-equality, vendor-tag, and representation behavior is unchanged — this change preserves the observable `©xyz` contract byte-for-byte and changes only the memory characteristics of how it is met.
- One line added to the release manual-verification checklist: cut a multi-minute 4K clip on a real device (the on-device UI path at real heap size is verified manually, matching the repo's existing manual-verification practice; no new instrumented large-file test is added).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `metadata-preserving-cut`: new requirement — container metadata handling SHALL NOT require buffering the entire file, and the preservation guarantee SHALL hold for files larger than the application memory class (which in practice also lifts the 2 GB `Int`-indexing ceiling).

## Impact

- `engine/src/main/java/dev/tagalong/engine/Mp4LocationFinalizer.kt` — streaming rewrite path (I/O layer only; box-parsing rules and failure semantics unchanged).
- `engine/src/main/java/dev/tagalong/engine/Mp4LocationMetadata.kt` — seekable box walker; `inspect(ByteArray)` overloads retained for tests where bytes are genuinely in memory.
- `engine/build.gradle.kts` — new `src/test` unit-test source set, `junit:junit` test dependency, `maxHeapSize = "256m"` for `:engine` unit tests.
- New test: `engine/src/test/java/dev/tagalong/engine/Mp4LocationHeapBudgetTest.kt` (+ a small `free`-padding fixture helper).
- Unaffected: `FfmpegCutEngine` call sites and flags, `MetadataReader` public model, UI, contract tests (which remain the on-device source of truth), the known re-encode rotation gap (still open, still gated for step 2).
- No new dependencies, no Android API changes, no `largeHeap` (the fix is streaming, not a bigger ceiling).
