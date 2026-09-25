# Design: cut-command-honesty

## Context

Current command (both modes, `FfmpegCutEngine`):

```
-ss <start> -i <src> -to <dur> -c copy|<encoders>
-map_metadata 0 -movflags +faststart+use_metadata_tags
```

followed by `Mp4LocationFinalizer.preserve(source, output)`, which splices the source's raw `©xyz` box bytes into `moov/udta` and self-verifies.

Measured current state (`comparisons/ffmpeg-baseline/` + `exiftool-restore.md`):

- `-map_metadata 0` is ffmpeg's default restated — the docs: *"By default, global metadata is copied from the first input file"*; a missing specifier *"defaults to global"*. It changes nothing.
- `+use_metadata_tags` is load-bearing: without it the vendor keys (`com.android.*`, `com.xiaomi.*`) are dropped and the `mvhd`/`tkhd`/`mdhd` date fields are zeroed.
- The default mapping exports the source `©xyz` as a normalized `location` dictionary entry; the mov muxer has no `©xyz` write path for MP4 output (QuickTime-`mov` only), so it instead writes a 3GPP `loci` box — re-encoding the decimal string to 16.16 fixed-point (truncated: 52.5182 × 2¹⁶ = 3,441,831.68 → 3,441,831 → 52.51819) — plus, with `use_metadata_tags`, a verbatim dictionary copy. Result: three location representations per cut, one of them wrong.
- `mov_write_loci_tag()` bails when the dictionary has no `location` entry (`if (!t) return 0`) — the suppression hook.

## Goals / Non-Goals

**Goals:**

- One location representation per output, equal to the source, for exactly the sources the finalizer already owns.
- Every flag in the command justifiable by a comment stating measured behavior.
- Per-representation behavior driven by the source, so no source shape silently loses location.

**Non-Goals:**

- No replacement of ffmpeg-kit; no custom muxer; no Media3.
- No reconciliation gate / rulebook engine (deliberately deferred).
- No change to UI, share-intake, gallery registration, or `DATE_TAKEN` handling.
- No attempt to restore what no path can restore (binary `com.video.file.type`, `mcvr` thumbnail).

## Decisions

### D1 — Suppression at the dictionary, not repair of the output

Delete the normalized location entries **before muxing** (`-metadata location= -metadata location-eng=`) so the drifted `loci` box and dictionary duplicates are never written.

*Alternative considered:* post-cut repair — flip the `loci` box's fourcc to `free` in the finalizer pass (4-byte in-place patch, no size change). Rejected: prevention keeps the output honest-by-construction and the output self-verification simple (assert absence, not "present-but-dead"). The repair path stays available as fallback if a variant location key slips past the deletion list — the contract test (D5) detects exactly that.

*Known limit:* `-metadata <key>=` deletes one exact key. Language-suffixed variants (`location-de`, …) would survive. Accepted: the corpus is Android-camera sources, which write the observed pair (`location`, `location-eng`); D5's test asserts *absence of any location dictionary entry* in QuickTime-shape outputs, so an unexpected variant fails the suite loudly instead of shipping.

### D2 — Deletion is conditional on the probe, and paired with the finalizer

The command is built only after `Mp4LocationMetadata.inspect(source)` has run (it is a streaming, heap-flat pass; today it runs inside `preserve()` — hoist it ahead of the ffmpeg execution, reusing the same inspected result, and pass it forward so `preserve()` need not re-read).

- Source has `©xyz` → add the deletion flags; the finalizer restores the exact bytes.
- Source has no `©xyz` (dictionary-location shapes) → **omit** the deletion flags; ffmpeg's verbatim dictionary copy carries location (today's behavior for this shape — the spec's regression guard).
- Source has no location at all → flags are irrelevant (nothing to delete); omit for command stability.

This makes the deletion→restore handover structural: the two halves cannot ship unpaired, because they are driven by the same probe result in the same code path.

*Alternative considered:* unconditional deletion + a new finalizer branch that synthesizes dictionary entries for non-QuickTime sources. Rejected: writing a location dictionary ourselves re-introduces the "comprehension-gated fidelity" failure mode this change exists to remove, for a source shape no fixture demonstrates.

### D3 — `+faststart` stays, with its cost recorded

Decision: **keep** the flag; record in the comment: it is a layout-only, progressive-download choice (writes `moov` before `mdat`); no consumer of a locally-saved gallery file needs it, and the cost of keeping it is a second full-file write pass at mux completion — relevant on the >2 GB 4K cuts the heap-budget work targets. Removal is deferred to its own change if we decide to pay for review; keeping it now preserves current on-device behavior byte-for-byte in every other respect. (Rationale requested by the project owner; the debate itself — measured: plain vs faststarted outputs both probe fine — is recorded here so the flag is reviewable.)

### D4 — Comments as measurements, not folklore

Each flag's comment cites *what was observed without it*, not what folklore claims:

- `-map_metadata 0:g` — "ffmpeg's default, stated explicitly: copy the whole global metadata dictionary from input 0. Deleting this changes nothing (verified: no-flag and this-flag arms produce identical metadata)."
- `+use_metadata_tags` — "writes the metadata dictionary in the Android mdta dialect (keys/ilst, like the camera's). Without it: vendor keys dropped and all mvhd/tkhd/mdhd date fields zeroed (verified 2026-09-22, comparisons/ffmpeg-baseline/)."
- The location deletion flags — reference this change + `movenc.c`'s `if (!t) return 0` bail-out, so the next reader knows why they must not be removed alone.

### D5 — Contract tests: singularity, not just presence

Extend the engine contract suite with, per fixture (driven by each source's representation, so the whole `sample-videos/` corpus participates without registration changes):

- **Absence of invention:** output contains no `loci` box unless the source had one.
- **Singularity (QuickTime-shape sources):** output contains the source `©xyz` payload byte-for-byte and **zero** location dictionary entries.
- **Regression guard (dictionary-shape sources):** output's location dictionary entry equals the source's.
- Both modes, same assertions ("guarantee holds identically in both modes" applies).

Probing uses the existing box-walk/inspect machinery (`Mp4LocationMetadata` and friends); `loci` detection is a fourcc walk under `moov` — no new dependency.

### D6 — Desktop pre-flight

Before on-device work, replicate the new arm on desktop (host ffmpeg 9.0.1, same flags) and exiftool-verify all D5 properties on both fixtures. Catches encoding-path regressions in seconds. Caveat recorded: host ffmpeg 9.0.1 vs the ffmpeg-kit-bundled 8.1.1 may differ; the on-device suite is the contract, the desktop run is a fast filter — treat desktop-green as necessary, not sufficient.

## Risks / Trade-offs

- **Bundled ffmpeg is 8.1.1, host verification ran on 9.0.1** → D6 is explicitly non-authoritative; the connected suite is the gate. If the older muxer behaves differently (e.g. writes location somewhere new), a D5 test fails on-device — which is the intended detection mode.
- **Unlisted location key variants survive deletion** (D1 known limit) → D5's singularity assertion is "zero location entries", so variants surface as test failures rather than silent ship.
- **A source carrying conflicting locations** (`©xyz` ≠ dictionary values) → the spec's "exactly one, as the source wrote it" resolves it: `©xyz` wins, the conflict is suppressed from the output. No UI surfacing (out of scope, deferred).
- **Hoisting the probe ahead of the cut** adds a second full streaming pass over the source (today `preserve()` inspects it after the cut) — a known, bounded I/O cost on huge files. Accepted for correctness; merging into a single pass is an open question, not a goal.
- **Deletion applies to the global dictionary** — a location carried only at stream level would pass through untouched, breaking the singular-location rule. The mov demuxer's location export is global-only, so no known producer hits this; a D5 failure would name it loudly if a device surprises us.

## Migration Plan

Additive flag/probe change; no data migration, no format concerns. Existing outputs (already shipped) keep their triple-location files; nothing back-fills them. Rollback = remove the two flags + the hoist — the deletion pair is safe to revert without touching the finalizer's behavior.
