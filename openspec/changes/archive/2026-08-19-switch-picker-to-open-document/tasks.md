## 1. Engine cleanup — remove location injection code

- [x] 1.1 Delete `FfmpegCutEngine.losslessCut(…, locationTag: String?)` overload and restore the single 4-arg `override fun losslessCut` as the sole implementation (no delegation indirection, no `-metadata location=` / `-metadata location-eng=` args)
- [x] 1.2 Remove `readLocationTag()` from `CutViewModel`
- [x] 1.3 Remove the `locationTag: String?` field from `PickedSource` in `CutUiState.kt`
- [x] 1.4 Update the `losslessCut` call in `CutViewModel.runCut` to remove the `locationTag` argument

## 2. Picker swap

- [x] 2.1 In `CutScreen.kt` replace `ActivityResultContracts.PickVisualMedia()` with `ActivityResultContracts.OpenDocument()` and update the launch call to pass `arrayOf("video/*")`; remove the `PickVisualMediaRequest` import
- [x] 2.2 In `CutViewModel.onVideoPicked`, remove the `DISPLAY_NAME` extension-inference block (the `when { rawDisplayName.contains('.') … }` branch that appended a MIME-derived extension when the name had no dot) — `OpenDocument` always returns a real filename; keep the final `displayPath` null-guard for `RELATIVE_PATH` as a general fallback per spec
- [x] 2.3 Remove `ACCESS_MEDIA_LOCATION` from `app/src/main/AndroidManifest.xml` and its explanatory comment

## 3. Test robot update

- [x] 3.1 Rename `PhotoPickerRobot` to `FilePickerRobot` (or update in place) and rewrite `selectFirstItem` to drive the `ACTION_OPEN_DOCUMENT` / Documents UI: wait for `com.android.documentsui` (or equivalent package on the emulator), navigate to the seeded video, and select it — run `adb shell uiautomator dump` while the chooser is open to confirm package name and widget tree
- [x] 3.2 Update `E2eCutTest` to call the updated robot and remove any remaining `pickerRedactedTags` references (the exclusion was already removed from the assertion; verify no stale comments reference the old gap)

## 4. Verify

- [x] 4.1 Build `:app:assembleDebug` — confirm zero errors
- [x] 4.2 Run `E2eCutTest` on `Pixel_7_API_34` — confirm the test passes including the location tag assertion
- [x] 4.3 Manually run the app, pick the Xiaomi fixture, cut, pull the output, and run `ffprobe` to confirm `location` and `location-eng` are present in the output
