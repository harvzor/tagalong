# Manual gallery verification

- Date: 2026-09-06
- Device: `emulator-5554` (Android 17 / API 37)
- Consumer: `com.google.android.apps.photos`
- Deterministic source/output checks: the engine lossless contract passed for both canonical samples, including exact raw `©xyz` payload comparison, vendor tags, creation time, and MediaStore date assertions.
- Emulator Google Photos check: attempted to open the source and saved MediaStore output through the Google Photos video viewer. Google Photos displayed `Can't play video.` for the available source/output rows, so the emulator location UI could not be inspected.
- Follow-up real-device check: the current build was installed on the wireless-debugging Pixel 10a, and the user confirmed the source and saved-output verification passed.

The real-device verification is the release evidence for Google Photos compatibility. The raw MP4 representation assertions remain the deterministic acceptance check.
