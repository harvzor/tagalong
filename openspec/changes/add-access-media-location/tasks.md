## 1. Manifest

- [x] 1.1 Add `<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />` to `app/src/main/AndroidManifest.xml`

## 2. Permission request in CutScreen

- [x] 2.1 Add a `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` launcher in `CutScreen` that stores the result in a local `var locationPermissionGranted` state, with an initial value from `ContextCompat.checkSelfPermission`
- [x] 2.2 Update the "Pick video" `Button`'s `onClick` to request the permission first (if not yet granted) and launch the file picker from the permission result callback; if already granted, launch the picker directly
- [x] 2.3 Show a one-time warning `Text` (e.g. "GPS location may not be preserved — location access was denied") below the "Pick video" button when `locationPermissionGranted` is false, styled consistently with `CutState.Error` text colour; hide it when the permission is granted

## 3. Play Store justification update

- [x] 3.1 Add a paragraph to the `ACCESS_MEDIA_LOCATION` section of `CLAUDE.md` (or a new dedicated section alongside the `ACTION_OPEN_DOCUMENT` justification) explaining that the permission is required to read — not collect — GPS already embedded in files the user explicitly selects, and that without it Android's media framework strips location from `openInputStream` regardless of which picker is used

## 4. Manual verification on a physical Xiaomi device

- [ ] 4.1 Build and install the debug APK on the Poco X5; pick a video that was shot with GPS enabled; confirm the ProbeCard `location` row shows the expected coordinates (not `—`)
- [ ] 4.2 Deny the `ACCESS_MEDIA_LOCATION` request; confirm the warning text appears and the file picker still opens; confirm the ProbeCard `location` row shows `—` and no crash occurs
- [ ] 4.3 Grant the permission from device Settings and re-pick the same video; confirm the warning is gone and the `location` row shows correctly
