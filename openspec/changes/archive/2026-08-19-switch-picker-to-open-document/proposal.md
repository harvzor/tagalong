## Why

`PickVisualMedia` (the Android Photo Picker) redacts three pieces of information at the URI level before the app can read them: the GPS location tags, the real `DISPLAY_NAME`, and the `RELATIVE_PATH`. This breaks the app's core metadata-preservation guarantee and forces workaround code (`readLocationTag`, manual `location`/`location-eng` injection into ffmpeg, display-name fallback logic) that is incomplete by design — it cannot recover tags it never saw. Switching the picker to `ACTION_OPEN_DOCUMENT` gives the app an unredacted byte stream, correct column values, and a simpler codebase.

## What Changes

- Replace `ActivityResultContracts.PickVisualMedia` with `ActivityResultContracts.OpenDocument` (type filter `video/*`) throughout the pick flow.
- Remove `CutViewModel.readLocationTag()` and the `locationTag` field on `PickedSource` — no longer needed because the cache copy retains all original tags.
- Remove the `FfmpegCutEngine.losslessCut(…, locationTag)` overload and the `-metadata location=` / `-metadata location-eng=` injection — `‑map_metadata 0` now copies these tags correctly from the unredacted cache file.
- Remove `ACCESS_MEDIA_LOCATION` from the manifest — it was declared for the Photo Picker path; `ACTION_OPEN_DOCUMENT` does not require it.
- Remove the `DISPLAY_NAME`/`RELATIVE_PATH` fallback logic in `onVideoPicked` that handled the Google Photopicker's numeric-ID and null-path redaction — those columns are correct with `ACTION_OPEN_DOCUMENT`.
- Update the `cut-workflow` spec: replace the GPS-via-permission requirement with an accurate statement of what the open-document picker guarantees; remove the "filename-only fallback" scenario (now always a full path).
- Update `E2eCutTest`: remove the `pickerRedactedTags` exclusion (already done); update the picker robot to drive `ACTION_OPEN_DOCUMENT`'s file-chooser UI instead of the Photo Picker thumbnail grid.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `cut-workflow`: GPS location preservation mechanism changes (no permission workaround; picker provides unredacted stream). Display path behaviour changes: real filename and relative path are always available; the "filename-only fallback" scenario is removed.

## Impact

- **`app/src/main/java/dev/tagalong/app/CutScreen.kt`** — picker launcher contract
- **`app/src/main/java/dev/tagalong/app/CutViewModel.kt`** — `readLocationTag`, `locationTag`, `onVideoPicked` cleanup
- **`app/src/main/java/dev/tagalong/app/CutUiState.kt`** — remove `locationTag` field from `PickedSource`
- **`engine/src/main/java/dev/tagalong/engine/FfmpegCutEngine.kt`** — remove `losslessCut` overload
- **`app/src/main/AndroidManifest.xml`** — remove `ACCESS_MEDIA_LOCATION`
- **`app/src/androidTest/java/dev/tagalong/app/`** — update `PhotoPickerRobot` and `E2eCutTest`
- **`openspec/specs/cut-workflow/spec.md`** — delta spec for changed requirements
