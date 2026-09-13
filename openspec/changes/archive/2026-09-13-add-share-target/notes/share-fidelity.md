# Share fidelity matrix (tasks §4)

Measures what byte stream each sender actually delivers to Tagalong. Raw `ffprobe` digests
recorded below; coordinates themselves are not documented in this repo beyond what the
corpus files already carry (see AGENTS.md note on the sample corpus).

## Method

For each (sender, device, sample):

1. Push the known-location sample from `sample-videos/` to the device gallery (fresh scan).
2. Share it to Tagalong from the sender app; note which URI authority/shape arrives
   (MediaStore row vs FileProvider copy — visible by probing the cache file `input.mp4`
   under the app's cache dir via `adb run-as`).
3. Cut any 2-second range; `ffprobe` the materialised cache bytes **and** the saved output.
4. Diff against the repository original: `location` (QuickTime ©xyz representation too —
   `Mp4LocationMetadata` is the authority), `creation_time`, device make/model tags.

## Matrix

| Sender | Device | URI shape received | location survives sender? | creation_time survives sender? | device tags survive sender? | Cut output OK? | Gallery date/location on output? |
|---|---|---|---|---|---|---|---|
| Google Photos | Emulator (Medium_Phone, API 37) | `input.mp4` **byte-identical** to `sample-videos/xiaomi-poco-x5.mp4` (`cmp` clean) | ✓ | ✓ | ✓ (Xiaomi model + marketname) | ✓ all tags carried | ✓ (result screen diff honest; gallery date = shoot date) |
| Google Photos | Pixel 10a (physical) | PENDING — pre-release checklist | — | — | — | — | — |
| System/AOSP gallery | Pixel 10a (physical) | PENDING — pre-release checklist | — | — | — | — | — |
| Google Photos | Xiaomi (if avail.) | PENDING — pre-release checklist | — | — | — | — | — |

Evidence for row 1 (2026-09-13): shared `xiaomi-poco-x5.mp4` from Google Photos with
`ACCESS_MEDIA_LOCATION` granted; `run-as cat cache/input.mp4 | cmp` against the repository
original was byte-identical, so the sender delivered the original file, and the app's
`setRequireOriginal` path kept the framework from redacting it. The cut output carried
`location`, `location-eng`, `creation_time`, and all Xiaomi tags unchanged.

Known expectation, not yet exercised: **permission-off share** — the same URI arrives
redacted by the framework at `openInputStream`; the cut succeeds with all non-location
tags and the diff card shows location absent on the source side. (Same contract as the
in-app picker; the emulator's Photos lacks the production share-sheet location-privacy
toggle, which remains a physical-device unknown.)

## Verdict (task 4.5)

**KEEP — 2026-09-13, user decision.** The empirical bet paid off in the case the emulator
could test: a real Google Photos build handed Tagalong a byte-identical original and the
full pipeline preserved it end-to-end. The physical-device rows above are downgraded to
the AGENTS.md *Pre-release manual verification* checklist (mirroring how the 4K/real-Pixel
large-file check is already handled); they gate the release, not the change. Revert remains
the documented remedy if a pre-release row ever comes back materially degraded.
