## 1. Home screen rework (`HomeScreen.kt`)

- [x] 1.1 Add resume-lifecycle permission state: evaluate `checkSelfPermission(ACCESS_MEDIA_LOCATION)` on each lifecycle resume (via `LocalLifecycleOwner` observer; `lifecycle-runtime-compose` already a dependency) so the UI reflects grant/revoke made in system settings
- [x] 1.2 Add the media-location block above the "Pick video" button: off-state = heading "Media location access is off" + mechanism-only copy ("GPS coordinates are stored inside your video files. Without permission to read them, Android strips GPS from every cut.") + "Enable media location access" button; on-state = inert line "📍 Media location access granted ✓" (no enable control, no warning, not clickable)
- [x] 1.3 Enable button has a single behavior: always `RequestPermission(ACCESS_MEDIA_LOCATION)`; no rationale heuristic, no settings navigation (design D2, revised)
- [x] 1.4 Simplify the pick path: "Pick video" always enabled, `onClick` calls `pickVideo.launch(arrayOf("video/*"))` directly; delete the `RequestPermission` branch in `launchPick`, the now-unused chaining state, and the red "GPS location may not be preserved" warning `Text`
- [x] 1.5 Update the long WHY comment on the pick launchers to describe the new choreography (permission owned by the Home control; picker untouched rationale unchanged)

## 2. Build & automated tests

- [x] 2.1 `./gradlew :app:assembleDebug` compiles cleanly (no Kotlin plugin/`kotlinOptions` additions per toolchain rules)
- [x] 2.2 With the verified AVD running, `./gradlew :app:connectedAndroidTest` passes — confirm `E2eCutTest` still finds the exact-text "Pick video" node (new copy must not contain that substring) and completes the pick → trim → cut flow with `ACCESS_MEDIA_LOCATION` auto-granted
- [x] 2.3 `./gradlew :engine:testDebugUnitTest` passes (should be untouched; run as regression guard)

## 3. Manual verification on device/emulator (auto-grant rules bypass the off-state)

- [ ] 3.1 Fresh install (permission not yet granted): Home shows the off panel above an enabled "Pick video"; tapping "Pick video" opens the system picker with **no** location dialog; a cut completes (location absent from output ProbeCard, as expected)
- [ ] 3.2 Tap "Enable media location access" → system dialog appears → Allow → status line flips to "📍 Media location access granted ✓" and the panel disappears; a subsequent cut of a GPS-bearing sample preserves location (ProbeCard shows it)
- [ ] 3.3 From the granted state, revoke via system settings and background/foreground the app → status reverts to the off panel without app restart (validates 1.1)
- [ ] 3.4 After repeated denials put the OS in silent auto-deny: tapping "Enable media location access" shows no dialog and does nothing visible — verify no crash and status stays "off"; force-restart the app and confirm the dialog is offered again

## 4. Spec & doc hygiene

- [x] 4.1 Confirm no changes needed outside `HomeScreen.kt` (manifest, `CutViewModel`, engine, `E2eCutTest` all untouched per proposal Impact)
- [ ] 4.2 After merge: `openspec archive --change "add-home-location-access-control"` so `home-screen` and `cut-workflow` main specs pick up the deltas
