## Why

GPS location tags are stripped from picked videos before they reach the cut engine: `materializeToCache` copies the source bytes via `ContentResolver.openInputStream`, and Android's media framework silently removes location data from that stream for apps that have not been granted `ACCESS_MEDIA_LOCATION`. The fix requires declaring and requesting the permission so the unredacted byte stream is delivered to the app and location tags survive through to the cut output.

## What Changes

- Add `ACCESS_MEDIA_LOCATION` to the app manifest.
- Add a runtime permission request for `ACCESS_MEDIA_LOCATION` that is triggered before launching the file picker. If denied, the user can still pick and cut; the app shows a one-time warning that GPS location may not be preserved in the output.
- No changes to the cut engine or `materializeToCache` byte-copy logic — once the permission is granted, the unredacted stream contains location tags and the existing `-map_metadata 0` path copies them automatically.

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `cut-workflow`: The GPS location preservation requirement gains a new sub-requirement: the app must declare and request `ACCESS_MEDIA_LOCATION` at runtime. Denial is non-blocking but the app warns the user. This is a user-facing flow change (new permission dialog) so it belongs in spec, not just design.

## Impact

- **`app/src/main/AndroidManifest.xml`** — add `<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />`
- **`app/src/main/java/dev/tagalong/app/CutScreen.kt`** — add `ActivityResultContracts.RequestPermission` launcher and invoke it before `pickVideo.launch(…)`
- **`app/src/main/java/dev/tagalong/app/CutViewModel.kt`** — no logic changes needed; the fix is in the permission and the manifest
- **`openspec/specs/cut-workflow/spec.md`** — delta spec adds permission-request requirement
- No new dependencies; `ACCESS_MEDIA_LOCATION` is part of the Android SDK (API 29+, already covered by minSdk 31)
