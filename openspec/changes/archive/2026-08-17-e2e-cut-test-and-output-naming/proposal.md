## Why

There is no automated regression safety net at the app layer: every change between step 1 and the rest of the roadmap is verified manually. At the same time, the output file is named `tagalong-{timestamp}.mp4`, which discards the source name and makes it impossible to know what a file is or where it came from without opening it.

## What Changes

- **Output filename** changes from `tagalong-{timestamp}.mp4` to `{originalBaseName}_from_{HH-MM-SS-mmm}_to_{HH-MM-SS-mmm}.mp4` (e.g. `xiaomi-poco-x5_from_00-00-00-500_to_00-00-03-500.mp4`).
- `PickedSource` gains an `originalDisplayName: String` field, populated by querying `MediaStore.MediaColumns.DISPLAY_NAME` on the picked Uri.
- `CutViewModel` derives the output display name from `originalDisplayName`, `startMs`, and `endMs` at save time; the timestamp-based name is removed.
- New `:app` instrumented test (`E2eCutTest`) drives the complete end-to-end flow on a running emulator: seeds the fixture into MediaStore, launches the app, interacts with the system photo picker via UIAutomator, triggers the cut, locates the output by its predictable name, and asserts metadata preservation against the source.
- Supporting test utilities: `MediaStoreSeeder` (insert/delete fixture in MediaStore), `PhotoPickerRobot` (UIAutomator interactions for the system photo picker).
- The fixture asset (`xiaomi-poco-x5.mp4`) is added to `app/src/androidTest/assets/`.
- UIAutomator and Compose test dependencies are added to `:app`.

## Capabilities

### New Capabilities

*(none — tests are not user-facing behavior)*

### Modified Capabilities

- `cut-workflow`: adds a requirement that the saved output's display name encodes the source video's base name and the cut's start and end timestamps.

## Impact

- `app/src/main/java/dev/tagalong/app/CutUiState.kt` — `PickedSource` gains `originalDisplayName`
- `app/src/main/java/dev/tagalong/app/CutViewModel.kt` — display name query on pick; output name derivation on save
- `app/src/androidTest/` — new test source set with three new files and the fixture asset
- `app/build.gradle.kts` — new `androidTestImplementation` dependencies (UIAutomator, Compose test rule, AndroidX test runner/rules)
- No API changes; no changes to `:engine` or `:cutdebug`
