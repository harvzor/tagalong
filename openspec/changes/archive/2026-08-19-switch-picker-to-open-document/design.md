## Context

See proposal.md — Why for motivation.

The current pick flow uses `ActivityResultContracts.PickVisualMedia`, which routes through the Android Photo Picker (system or Google Play Store module). That picker redacts three MediaStore column values at the URI level before the app ever reads them: the GPS location tags are stripped from the `openInputStream` byte stream, `DISPLAY_NAME` may be replaced with an internal numeric ID, and `RELATIVE_PATH` is nulled out. The engine's `-map_metadata 0` flag faithfully copies what it receives, so the redaction is invisible to the engine but fatal to the guarantee.

`ActivityResultContracts.OpenDocument` (backed by `ACTION_OPEN_DOCUMENT`) bypasses the Photo Picker and hands the app a direct, unredacted content URI. All three columns resolve correctly.

## Goals / Non-Goals

**Goals:**
- Picker returns an unredacted byte stream so that `-map_metadata 0` copies all source tags (including GPS) without custom injection.
- Picker returns real `DISPLAY_NAME` and `RELATIVE_PATH` values so the output filename and the path label are correct without fallback heuristics.
- Remove all code that exists only to paper over the Photo Picker's redaction.

**Non-Goals:**
- Restoring a gallery-thumbnail browsing UI (the `OpenDocument` chooser shows a file tree; improving its appearance is a separate concern).
- Adding re-encode mode (step 2 — unrelated).

## Decisions

### D1 — Use `ActivityResultContracts.OpenDocument` with `arrayOf("video/*")`

`OpenDocument` is the standard Android contract for requesting access to an arbitrary file with persistent URI permission. It returns a `content://` URI the app can open via `ContentResolver.openInputStream` with full, unredacted bytes.

Alternatives considered:

| Option | Why rejected |
|---|---|
| `PickVisualMedia` + `MediaStore.setRequireOriginal` | `setRequireOriginal` on Photo Picker URIs throws `UnsupportedOperationException` on the Play Store module; works only on API 33+ system picker. Requires runtime `ACCESS_MEDIA_LOCATION` grant on API 34+. Fragile across device/OS combinations. |
| `PickVisualMedia` + `MediaMetadataRetriever` pre-read | Tested on-device: `MediaMetadataRetriever` on Photo Picker URIs also returns null for location. Picker redaction occurs before `MediaMetadataRetriever` reads the stream. |
| Custom gallery picker | Significant UI scope; out of scope for this app's design principles. |

### D2 — Remove `readLocationTag`, `locationTag` on `PickedSource`, and the `losslessCut(…, locationTag)` overload

With an unredacted cache file, `-map_metadata 0` copies location tags — and every other format tag — automatically. The injection code becomes dead weight and is removed entirely. The `CutEngine` interface is unchanged (the 4-arg `losslessCut` override on `FfmpegCutEngine` that delegated to the now-removed 5-arg overload also goes away).

### D3 — Remove `ACCESS_MEDIA_LOCATION` from the manifest

The permission was declared for the Photo Picker path (where it was insufficient anyway). `OpenDocument` does not require it. Removing it avoids granting an unused permission.

### D4 — Remove the `DISPLAY_NAME` extension-inference and RELATIVE_PATH null-guard

`OpenDocument` URIs return the real filename in `DISPLAY_NAME` (always has an extension) and the real path in `RELATIVE_PATH`. The fallback branches in `onVideoPicked` that handled the Photopicker's numeric-ID and null-path are deleted. The general "filename only when relative path unavailable" fallback in `displayPath` is kept — other storage providers could theoretically omit `RELATIVE_PATH` — but the Photopicker-specific comments and the extension-inference block are removed.

### D5 — Update `PhotoPickerRobot` in the test suite

The E2E test currently drives the Photo Picker's thumbnail grid via UIAutomator. With `OpenDocument` the system presents a file-chooser UI; the robot must navigate to the seeded file's location and select it. The `MediaStoreSeeder` still inserts the fixture the same way; only the UIAutomator interaction changes.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `OpenDocument` UI is less polished than the Photo Picker (file tree vs. thumbnail grid) | Acceptable for the app's target audience; can be revisited independently |
| Some storage providers may not populate `RELATIVE_PATH` even with `OpenDocument` | The general fallback (filename-only display) is kept; this is not a regression |
| UIAutomator path for the `OpenDocument` chooser varies by Android version and OEM launcher | `PhotoPickerRobot` must be robust to the emulator's Documents UI; tested on Pixel_7_API_34 |

## Open Questions

*(none — all decisions needed before task breakdown are resolved above)*
