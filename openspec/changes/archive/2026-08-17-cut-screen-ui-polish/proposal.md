## Why

The cut screen is functional but visually rough: the empty state leaves a lone button stranded in the top-left corner, picked-video controls are left-aligned and narrow, both action buttons carry equal visual weight even though "Pick a different video" is a secondary escape action, and there is no indication of which file was picked. These issues make the app feel unfinished for a first real user.

## What Changes

- **Empty state**: center the "Pick video" button vertically and horizontally on the screen so it sits in the natural thumb zone.
- **Button width**: apply `fillMaxWidth()` to all action buttons so they span the screen like standard Material 3 controls.
- **Button hierarchy**: demote "Pick a different video" from a filled `Button` to an `OutlinedButton`, giving "Cut and save" clear visual priority.
- **Video preview width**: expand the preview to fill the available screen width (with aspect ratio preserved) instead of rendering at its intrinsic narrow size.
- **Source path display**: show the picked file's relative gallery path (e.g. `DCIM/Camera/PXL_20240101.mp4`) above the video preview. When the Google Photopicker is in use and `RELATIVE_PATH` is unavailable, fall back to the filename only.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `cut-workflow`: adds a requirement that the app displays the picked source video's path (or filename) to the user while they are setting the trim range.

## Impact

- `app/src/main/java/dev/tagalong/app/CutScreen.kt` — layout and button style changes
- `app/src/main/java/dev/tagalong/app/CutUiState.kt` — add `displayPath: String` field to `PickedSource`
- `app/src/main/java/dev/tagalong/app/CutViewModel.kt` — extend MediaStore query to include `RELATIVE_PATH`; build `displayPath` from the two columns
- No new dependencies; no API or engine changes
