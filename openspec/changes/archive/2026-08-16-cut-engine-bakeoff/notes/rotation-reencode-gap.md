# Finding: ffmpeg-kit re-encode mode loses the rotation *signal* (not the pixels)

Confirmed on-device (Pixel_7_API_34 emulator) and cross-checked on desktop ffmpeg 8.1.1
(same major version as the bundled `ffmpeg-kit-full-gpl-x86_64-6.0-20251025` build — log
line `Lavf62.3.100` matches).

## What happens

`xiaomi-poco-x5.mp4` carries its rotation as a container-level (MOV `tkhd`) display matrix:
`rotation of -90.00 degrees`, which Android's `MediaMetadataRetriever` normalizes to `90`.

- **Lossless** (`-c copy`): the matrix is copied verbatim. Source and output both read `90`. ✅
- **Re-encode** (`-c:v libx264 ...`): re-encoding decodes and re-muxes fresh packets, so
  there is no existing side data to copy forward.
  - Without `-noautorotate`, ffmpeg's default behavior would auto-insert a rotate filter,
    physically rotate the decoded pixels to upright, and (correctly, from ffmpeg's point of
    view) drop the now-redundant rotation tag. That's the literal failure mode the spec
    forbids ("preserve orientation as a signal ... not baked into frames") — so the engine
    uses `-noautorotate` to keep raw, unrotated pixels.
  - Re-stamping the rotation onto the freshly-encoded stream so the *signal* is still
    present turned out to have **no working CLI-only path** on this ffmpeg build:

| Attempt | Result |
|---|---|
| `-metadata:s:v:0 rotate=90` (output option) | Logged `Conversion of a 'rotate' metadata key ... is deprecated`; value not present in the muxed output (checked via ffprobe — no tag, no side_data). |
| `-display_rotation:v:0 90` (tried output-side) | Hard error: `"you are trying to apply an input option to an output file"`. |
| `-display_rotation:v:0 90` (moved input-side, before `-i`, per the error's own suggestion) | Accepted, no error — but has no effect on the re-encoded output (it only feeds the auto-rotate *decode* filter, which is disabled anyway by `-noautorotate`). |
| `-bsf:v h264_metadata=display_orientation=insert:rotate=90` | **Does** land in the output — ffprobe shows a frame-level `3x3 displaymatrix` side data derived from the injected Display Orientation SEI. But Android's `MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION` does not read it — output still reports rotation `0`. Android's reader appears to only honor the container-level (`tkhd`) matrix, not codec-level SEI. |

## Why this wasn't chased further

Every avenue above is real ffmpeg/Android-platform behavior, not a scripting mistake (each
was independently reproduced on desktop ffmpeg 8.1.1 before being ruled out). Actually
writing a `tkhd` display matrix for a freshly-muxed stream in this ffmpeg build appears to
require going around the CLI's declarative metadata options entirely (e.g. a custom muxer —
`androidx.media3:media3-muxer`'s `Mp4Muxer`, already a project dependency, accepts
`Format.rotationDegrees` per track and would very likely solve this). That's real
implementation work, disproportionate to a bake-off whose job is to reach a decision, not
productionize the losing/winning arm's every code path.

## Disposition (feeds task 5)

- Does **not** change the bake-off decision: Media3 Transformer already fails both modes on
  a strictly more fundamental requirement (drops all `com.android.*`/`com.xiaomi.*` tags
  and overwrites `creation_time` — see notes/results.md).
- **Does** carry forward as a concrete, scoped follow-up for the app-build phase: ffmpeg-kit
  re-encode mode needs either (a) a custom muxing step (e.g. media3-muxer for the final mux,
  ffmpeg for decode/encode) or (b) an upstream ffmpeg-kit/ffmpeg fix, before re-encode mode
  can ship. Lossless mode has no such gap.
