## Context

See proposal.md for motivation. Current state at `f97fc44`: `Mp4LocationFinalizer.preserve` and `Mp4LocationMetadata.inspect(File)` each buffer whole files into `ByteArray`s — peak ≈ 1×source + 2×output in heap, plus a hard 2 GB ceiling from `Int`-indexed file positions. Both classes are pure JVM (no Android imports), which is what makes host-side testing possible. The observable contract they must keep is pinned by the on-device `CutEngineContractTest` (raw `©xyz` payload equality, vendor tags, `creation_time`, MediaExtractor-readable tracks) and the existing fail-loud semantics for layouts the finalizer cannot prove safe. Every other pipeline stage (`materializeToCache`, ffmpeg-kit, `DateTakenStore`) already streams, so `FfmpegCutEngine`'s calls into these two classes must keep working unchanged.

## Goals / Non-Goals

**Goals:**

- Bounded memory for probing and finalizing, independent of file size (target: fixed small buffer + a box-descriptor tree proportional to box count, not bytes).
- Lift the 2 GB `Int`-indexing ceiling as a consequence, not a special case.
- Byte-identical observable output for the current test corpus: same splice semantics, same self-verification, same temp-file-and-swap installation, same exceptions.
- A host-side (no emulator) regression test that fails on today's code and passes after the change.

**Non-Goals:**

- The re-encode rotation gap (unchanged, still the step-2 blocker).
- Fragmented MP4 support (still rejected loudly, as today).
- `android:largeHeap` or any other "bigger ceiling" approach.
- FFmpeg flag changes, output layout changes (faststart stays), or performance tuning beyond removing the pathological buffering.
- An instrumented large-file `@LargeTest` — deliberately excluded; the on-device large-file path is covered by the manual 4K check instead (user decision during exploration).

## Decisions

### D1 — Two-phase rewrite: stream-splice during copy, then patch fixed-width fields in place

`preserve` becomes: (1) walk the output's box tree seekably via `RandomAccessFile` into a small descriptor tree (positions become `Long` throughout); (2) compute the splice plan exactly as today (replace contiguous `©xyz` range / append into `udta` / synthesize `udta`); (3) stream-copy original output → temp file through `FileChannel`, splitting the copy around the splice offset and inserting the raw source `©xyz` bytes; (4) after the copy, seek into the temp file and patch, in place, only the fixed-width fields affected by `delta`: enclosing `udta`/`moov` sizes and every `stco`/`co64` entry with a value past the splice. Because sizes and chunk offsets are fixed-width, patching never re-shifts bytes, so the copy happens exactly once and no in-memory file copy ever exists.

**Alternative considered:** single-pass patch-while-copying (patch fields as they stream through the buffer). Rejected: `stco` entries can appear before the splice point in non-faststart layouts, so the final offset values aren't known at first touch; two-phase is simpler to reason about and the second phase touches only a few hundred bytes.

**Alternative considered:** patch ffmpeg's output in place (no temp file). Rejected: a crash mid-rewrite would leave a corrupt cache output; temp-file-and-swap keeps today's rollback story (`FfmpegCutEngine` deletes the output on any finalizer failure).

### D2 — Inspect via seekable walk; keep `inspect(ByteArray)` for in-memory callers

`inspect(File)` opens a `RandomAccessFile`, walks root → `moov` → `udta`/`meta`, and reads only the `©xyz` payloads and `keys` entries (tens of bytes). `MediaProbe.locationRepresentation` and `quickTimePayload` semantics are unchanged. The `inspect(ByteArray)` overload stays for unit fixtures and already-in-memory bytes. Box-grammar rules (header forms, `meta` child-start heuristic, container set, `©` handling) are reused verbatim — this change is I/O shape, not parsing semantics.

### D3 — Self-verification runs against the temp file, seekably

Today's "re-inspect rewritten bytes, throw unless payloads match" guard becomes "re-inspect the temp file via D2 before installing". The guarantee (never install an output that lost the payload) is preserved with zero full-file reads.

### D4 — Long file positions everywhere; arrays only for atoms

File offsets and box sizes become `Long`; `ByteArray` allocation is permitted only for individual metadata atoms with an explicit small-size guard (a >-few-MB `©xyz`/`keys` atom is absurd and still fails loudly). This is what removes the 2 GB behavior: no code path converts a file position to `Int`.

### D5 — Regression test on the host JVM with a module-pinned heap

New `engine/src/test` source set (first unit tests the module has). `testOptions.unitTests.all { maxHeapSize = "256m" }` — a deliberate phone-heap simulation, documented with a comment; "module-wide" is acceptable because this test is currently the only occupant. Fixtures are real corpus samples grown at test time by appending valid root-level `free` padding boxes (deterministic, ~1 s, no large binaries in git; the parser treats `free` as opaque). The test calls `preserve()` (padded source + padded output, > heap) and `inspect(File)` (padded file) and asserts payload equality — red today with `OutOfMemoryError` at `Mp4LocationFinalizer.kt:21`, green after.

**Alternative considered:** test forks its own `-Xmx256m` child JVM (per-test isolation). Rejected as unnecessary cleverness: with zero other unit tests in the module, the module-wide pin is equivalent and needs no fork/exit-code ceremony to maintain.

**Alternative considered:** instrumented `@LargeTest` deriving size from `ActivityManager.memoryClass`. Rejected by the user (solo-maintainer project, no CI); the real-device 4K manual check covers the UI path.

### D6 — Verification strategy: the corpus contract tests are the equivalence oracle

Because outputs must stay byte-equivalent for the corpus, TDD order is: land the unit tests first (red), implement streaming (unit tests green), then re-run the existing instrumented suites — `CutEngineContractTest` (payload equality, vendor tags, rotation, gallery date, MediaExtractor readability) and `E2eCutTest` — unchanged. Any behavioral drift shows up there, not in new device tests.

## Risks / Trade-offs

- [Risk] `stco`/`co64` patching in the new pass mis-converts offsets and corrupts media → The splice/patch arithmetic is unchanged from `f97fc44` (only `Int`→`Long` and I/O shape); the corpus contract tests assert MediaExtractor can still read samples, and the fail-loud guards (chunk pointing into the rewritten range, negative/overflow results) remain.
- [Risk] `FileChannel.transferTo` performs partial writes → Copy loop until position reaches the region end (standard channel idiom), covered by the byte-equivalence contract tests.
- [Risk] The 256m unit-test heap slows or constrains future unit tests → 256 MB is ample for ordinary JVM unit tests; the constraint is a commented line in `build.gradle.kts` and can be re-scoped to a dedicated `Test` task later if it ever bites.
- [Risk] >heap-sized fixture writes (~400 MB per run) into the temp dir → JUnit `TemporaryFolder` cleans up; host disk cost is ~1–2 s on SSD and never runs on the emulator.
- [Risk] Manual-only coverage of the on-device large-file path could miss a device-specific interaction (e.g. cache-dir free space) → Accepted deliberately; the checklist line in manual verification names a multi-minute 4K clip, and `writeReplacement` already fails loudly if the parent directory misbehaves.
- [Trade-off] Keeping `inspect(ByteArray)` means two I/O shapes to test → The byte overload stays exercised by existing unit-style tests and keeps the pure box grammar directly unit-testable without files.

## Migration Plan

1. Add `engine/src/test` + heap-pinned `Mp4LocationHeapBudgetTest` with padded fixtures; run it and record the expected red (OOM at `preserve`) on current code.
2. Implement D1–D4 in the two engine files (no call-site changes).
3. Unit tests green on host; then `:engine:connectedAndroidTest` and `:app:connectedAndroidTest` on `Pixel_7_API_34` to prove corpus byte-equivalence.
4. Add the manual 4K checklist line to release verification docs.
5. Rollback is `git revert` of the two engine files + test wiring; no data or format migration — outputs on disk are unaffected by which code path wrote them.
