# Tagalong: Video Editor

**Trim and convert video. Your metadata comes along.**

Build guidance for the app. This document defines what the app is and what it must guarantee. It does not prescribe implementation.

---

## The problem

When you trim a clip in a typical mobile editor, the output loses the date it was shot, the GPS coordinates, and the camera information the phone recorded. The video is fine; the record of when and where it happened is gone. The gallery then files it under today's date, and the original context is unrecoverable.

Tagalong exists to fix that one problem. Every design decision should be read against it.

## The two modes

### Lossless cut

Copies the video and audio streams through without re-encoding. No quality loss, and the cut finishes in roughly the time it takes to write the output.

Cuts snap to the nearest keyframe, so the start point can land up to a second or two from where the slider was. This is a real constraint, not a bug — surface it in the UI rather than hiding it, and don't silently fall back to re-encoding to work around it.

### Cut and re-encode

Re-encodes the output, allowing frame-accurate cut points and changes to resolution, bitrate, or codec. Slower, and it costs some quality.

**Metadata must be preserved identically in both modes.** This is the core requirement. The choice between modes is about pixels and speed, never about whether the video keeps its date. A metadata guarantee that holds in one mode and not the other is a failed implementation.

## What must be preserved

- **Creation date** — the date the video was shot, not the date it was edited
- **Location** — GPS coordinates, where the source recorded them
- **Camera information** — make, model, and manufacturer-specific tags
- **Orientation** — portrait clips stay portrait, with rotation properly signalled rather than baked into the frames
- **Gallery date** — the date the phone's gallery displays, which is stored separately from the file's internal metadata and is the thing most apps silently get wrong

The last item is not the same as the first. An output whose container metadata is perfect but which appears in the gallery under today's date has failed the user's actual goal. Treat file-level and system-level metadata as two separate obligations.

## Out of scope

No multi-track timeline. No filters, transitions, stickers, text overlays, or music. No account, no cloud, no export watermark.

These exclusions are deliberate. Feature requests that push toward a general-purpose editor should be declined, because every addition dilutes the one guarantee the app exists to make. Two operations, done without surprises.

## Design principles

1. **Never silently degrade.** If metadata can't be preserved for a given file or format, tell the user before the export, not after.
2. **Never silently re-encode.** If the user chose lossless, the output is lossless or the operation fails with an explanation.
3. **The original is never modified.** Always write a new file.
4. **Assume the output will be verified.** Users should be able to compare source and output with exiftool or MediaInfo and find the tags matching apart from duration and file size.
