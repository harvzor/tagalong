# Tasks: cut-command-honesty

## 1. Desktop pre-flight (design D6)

- [x] 1.1 Run host ffmpeg (9.0.1) with the proposed command on both corpus fixtures (`-metadata location= -metadata location-eng=`, other flags unchanged), both modes
- [x] 1.2 exiftool-assert per fixture: no `loci` box in output; `©xyz` payload byte-equal to source; zero location dictionary entries (`location`, `location-eng` absent)
- [x] 1.3 Confirm the re-encode arm suppresses `loci` the same way (same dictionary path)
- [x] 1.4 Record the new arm beside the existing evidence in `comparisons/ffmpeg-baseline/` (before/after dumps, short README note)

## 2. Engine: conditional suppression + honest flags (design D1-D4)

- [x] 2.1 Hoist `Mp4LocationMetadata.inspect(source)` ahead of ffmpeg execution in `FfmpegCutEngine`, passing the inspected result forward so `Mp4LocationFinalizer.preserve` reuses it (no second inspect pass; keep the streaming/heap-flat guarantees from the main spec's memory requirement)
- [x] 2.2 Add `-metadata location= -metadata location-eng=` to both modes' commands only when the probe reports a source QuickTime `©xyz` location
- [x] 2.3 Write the flag comments per design D4 (`-map_metadata 0:g` default-restated; `+use_metadata_tags` measured responsibility; `+faststart` layout-only with cost recorded; location deletions documented as the pair of the finalizer)
- [x] 2.4 Keep `Mp4LocationFinalizer` behavior unchanged for the QuickTime-shape path; verify its existing self-verification still runs after the suppression change

## 3. Engine tests (design D5)

- [x] 3.1 Test probes: detect a `loci` box under `moov` (fourcc walk); enumerate any location dictionary entries in the output's keys/ilst
- [x] 3.2 Assertion: no `loci` box in output unless the source carried one - both modes, all discovered fixtures
- [x] 3.3 Assertion (QuickTime-shape sources): output's `©xyz` payload byte-equal to source AND zero location dictionary entries - both modes
- [x] 3.4 Assertion (dictionary-location sources, add a synthetic fixture per `test-fixtures` conventions if none exists): output retains the source's location dictionary value - both modes
- [x] 3.5 Run the full `:engine` connected suite on the Medium_Phone AVD (the 8.1.1-bundled muxer is authoritative; desktop green is necessary, not sufficient)
  <!-- 2026-09-22: full suite run. 6/7 green. Sole red = reencodeCut rotation signal (expected 90, was 0) — the pre-existing rotation gap documented in AGENTS "Known open gaps"; not touched by this change. All location-singularity assertions (lossless + reencode) and the new 3.1/3.4 tests pass. -->

## 4. Close-out

- [x] 4.1 Confirm no regressions in existing engine requirements (`creation_time`, vendor tags, rotation signal, `DATE_TAKEN`, large-file heap tests)
- [ ] 4.2 Spot-check one cut end-to-end: output plays, Google Photos shows location + original capture date
- [ ] 4.3 Note in `exiftool-restore.md` and `comparisons/google-photos/README.md`, where they speculate about Android's location reading, that this change makes the app's output carry a single authoritative `©xyz` (one line each; the device rows in the pre-release checklist remain)
  <!-- 2026-09-22: google-photos/README.md note added. BLOCKED: exiftool-restore.md does not exist anywhere in the repo (referenced only by this change's own design/tasks), and the gallery comparison READMEs are diff-only with no location-reading prose to annotate. Needs an owner decision on where the second note belongs (or whether to create the file). -->
