# Bake-off results

Run on `Pixel_7_API_34` (API 34) emulator, `xiaomi-poco-x5.mp4` fixture, both cut modes,
full `metadata-preserving-cut` assertion set (`CutEngineContractTest`). Reproduced across
two consecutive full runs — stable, not flaky.

## Results table

| Requirement | ffmpeg-kit LOSSLESS | ffmpeg-kit REENCODE | Media3 LOSSLESS | Media3 REENCODE |
|---|---|---|---|---|
| All source format tags present & unchanged (incl. `com.android.*`/`com.xiaomi.*`) | ✅ | ✅ | ❌ drops all 3 vendor tags | ❌ drops all 3 vendor tags |
| `creation_time` retained | ✅ | ✅ | ❌ overwritten to export time | ❌ overwritten to export time |
| `location` retained | ✅ | ✅ | ✅ | ✅ |
| Rotation signal preserved (not baked into pixels) | ✅ | ❌ signal lost (pixels correct — see notes/rotation-reencode-gap.md) | *(not reached — fails earlier)* | *(not reached — fails earlier)* |
| Frame dimensions unchanged | ✅ | ✅ | *(not reached)* | *(not reached)* |
| Source file untouched (bytes + hash) | ✅ | ✅ | ✅ | ✅ |
| `MediaStore.DATE_TAKEN` == source capture date, survives scan | ✅ | ✅ | *(not reached)* | *(not reached)* |
| **Overall (this run)** | **PASS** | **FAIL** (1 of 7) | **FAIL** (2 of 7 checked) | **FAIL** (2 of 7 checked) |

## Baggage

| | ffmpeg-kit (`com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`) | Media3 Transformer (`androidx.media3:media3-transformer:1.8.0`) |
|---|---|---|
| License | LGPL/GPL (`-full-gpl` variant is GPL) | Apache-2.0 |
| Size | Large — bundles a full ffmpeg build (`libavcodec`, `libavformat`, `libavfilter`, `libavdevice`, `libswresample`, `libswscale`, armv7a+neon variants, ~tens of MB) | Small — no bundled native codec binary, uses platform `MediaCodec` |
| Maintenance | Single-maintainer fork of a retired upstream (arthenica ffmpeg-kit); command syntax is portable to `ffmpeg-kit-next` later (D3) | First-party Google/AndroidX, same family as future Compose UI |
| Toolchain fit | No AAR clash with AGP 9.2.1/Gradle 9.5 confirmed (notes/toolchain.md) | No AAR clash confirmed |

## Decision (task 5.2)

**ffmpeg-kit wins.** Media3 Transformer fails the contract's most fundamental requirement —
"no source tag lost" — in *both* modes, dropping every manufacturer-specific tag and
corrupting `creation_time` to the export timestamp. That alone disqualifies it regardless of
its smaller footprint and Apache-2.0 license; per task 5.2, "if only ffmpeg passes, that
decides it." ffmpeg-kit's lossless mode passes the full contract cleanly. Its re-encode mode
has one confirmed, scoped gap (rotation signal, not pixels — see
notes/rotation-reencode-gap.md) that does not change the decision but is carried forward as
a concrete follow-up for the app-build phase.

## Product-scope check (task 5.3)

**Not triggered.** The concern was "neither engine preserves `DATE_TAKEN`." ffmpeg-kit
preserves it in both modes (verified by write → commit → read-back after
`MediaScannerConnection.scanFile`). No product-scope rethink is needed.

## Handoff (task 5.4)

- **Winner: ffmpeg-kit** (`com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`), command shape per
  design D3, with the `-noautorotate` fix for re-encode mode folded in.
- **Migration target**: `ffmpeg-kit-next` remains the long-term target once it ships a
  prebuilt package (design D3, out of scope here).
- **Carried-forward gap**: re-encode mode's rotation-signal loss (notes/rotation-reencode-gap.md)
  needs a fix (most likely: mux the re-encoded output via `androidx.media3:media3-muxer`,
  which accepts `Format.rotationDegrees` directly, instead of ffmpeg's own mov muxer) before
  re-encode mode ships.
- **Media3 Transformer arm kept side-by-side** (`Media3CutEngine.kt`,
  `Media3CutEngineTest.kt`, `media3-*` androidTest dependencies) rather than deleted, per
  request — the module (`cutdebug/`, renamed from `cutspike/`) is meant to persist as an
  on-device debugging/verification harness, so having both engines runnable and comparable
  side-by-side is more useful than removing the losing one. Its tests are *expected* to
  fail; that failure is the documented finding above, not a regression.
