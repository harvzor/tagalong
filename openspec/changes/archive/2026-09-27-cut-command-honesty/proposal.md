# Proposal: cut-command-honesty

## Why

Measurements (`comparisons/ffmpeg-baseline/`, 2026-09-22) show the cut command carries flags nobody chose deliberately — and two of them do damage or nothing at all. The default metadata mapping makes ffmpeg **invent a 3GPP `loci` location box the camera never wrote**, with the coordinates silently re-quantized (`+52.5182` → `52.51819`, ~1.2 m drift on the Xiaomi sample, plus a fabricated `Altitude: 0 m`) — while the app's own finalizer then pastes the *true* location back, so today's outputs carry up to three different location representations. Meanwhile `-map_metadata 0` restates the default (a no-op) and the one flag that genuinely preserves vendor keys and capture dates (`+use_metadata_tags`) has nothing recording what it does.

This makes the cut command unreviewable: nobody can say which flag earns its place, so nobody can say what the output's metadata actually is.

## What Changes

- FFmpeg stops handling location entirely on cuts whose source carries a QuickTime `©xyz` location (the case the finalizer already owns): the command adds `-metadata location= -metadata location-eng=` to delete the normalized location entries before muxing. This prevents the drifted `loci` box and the duplicate dictionary entries from ever being written.
- The location deletion and the finalizer become an explicit, documented **pair** — the deletion removes ffmpeg's mistranslation; the finalizer restores the source's exact `©xyz` bytes. Neither ships without the other.
- For sources that carry location *only* as a normalized/dictionary entry (no `©xyz`), the deletion flags are **not** applied, so ffmpeg's existing dictionary copy of the source location is retained (today's behaviour for that shape; no regression either way).
- `-map_metadata 0:g` stays, written explicitly, with a comment stating it is ffmpeg's default ("copy the global metadata dictionary from input 0").
- `-movflags +faststart+use_metadata_tags` gains comments recording what each does: `use_metadata_tags` is load-bearing (without it, vendor keys and the capture dates are lost); `faststart` is a layout-only progressive-download flag, kept, with its cost recorded.
- New engine contract assertions (both modes, both fixtures): the output contains **no** 3GPP LocationInformation box, contains the source's `©xyz` payload **byte-for-byte**, and carries location **exactly once** (no normalized location entries alongside).

No user-visible UI changes. No new permissions. Output files change in one observable way: the drifted `loci` box and duplicate location entries disappear.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `metadata-preserving-cut`: a new requirement — when the source carries a QuickTime `moov/udta/©xyz` location, the output SHALL carry exactly one location representation, equal to the source payload, and SHALL NOT contain a 3GPP LocationInformation (`loci`) box or a normalized location entry derived from ffmpeg's translation; when the source carries location in no QuickTime `©xyz` form, the output SHALL retain the source's location in the representation the source used.

## Impact

- `engine/` — `FfmpegCutEngine` command construction (both modes), conditional on the source's location representation (already probed by `Mp4LocationMetadata.inspect`, which the finalizer pipeline runs today); `Mp4LocationFinalizer` unchanged unless the conditional handover needs a hook.
- `engine/src/androidTest` — contract-test additions for the location-singularity assertions (new assertions are per-file, driven by each source's representation, so they run across the whole `sample-videos/` corpus automatically).
- Desktop evidence: `comparisons/ffmpeg-baseline/` records the before-state; a verification cut with the new flags is the acceptance evidence.
- No app-layer, UI, permission, or dependency changes.
