## 1. Night-aware platform theme

- [x] 1.1 Read `lightColorScheme().background` and `darkColorScheme().background` values so the window background can mirror them exactly (design D3); record both in `app/src/main/res/values/colors.xml` and `app/src/main/res/values-night/colors.xml`, each commented with the scheme property it mirrors
- [x] 1.2 Create `app/src/main/res/values/themes.xml` defining `Theme.Tagalong`, parent `@android:style/Theme.Material.Light.NoActionBar`, window background from 1.1
- [x] 1.3 Create `app/src/main/res/values-night/themes.xml` overriding `Theme.Tagalong` with parent `@android:style/Theme.Material.NoActionBar` and the dark window background
- [x] 1.4 Point `app/src/main/AndroidManifest.xml`'s application `android:theme` at `@style/Theme.Tagalong`, replacing the hardcoded `Theme.Material.Light.NoActionBar`

## 2. Compose colour scheme

- [x] 2.1 In `MainActivity`, pass an explicit colour scheme to the single `MaterialTheme` call, selected from the system night mode (design D1); no `colorScheme` argument is added anywhere below that call
- [x] 2.2 Confirm by inspection that no composable needed an edit — `grep` `app/src/main` for literal colours and any hardcoded `colorScheme` value, expecting zero hits
- [x] 2.3 Leave `enableEdgeToEdge()` unmodified (design D4) and leave `PlayerView` unstyled (design D5)

## 3. Build and automated tests

- [x] 3.1 `./gradlew :app:assembleDebug` builds cleanly with the new resource files
- [x] 3.2 `./gradlew :engine:testDebugUnitTest` passes (no engine change expected)
- [x] 3.3 With the emulator running: `./gradlew :app:connectedAndroidTest` passes, confirming the suites make no appearance-dependent assertions

## 4. Manual appearance pass (device/emulator, per spec `app-appearance`)

- [x] 4.1 Device in dark mode, cold start: app opens dark; no light frame flashes before content; Home, Trim, Result and About all dark (specs: "Launch on a device in night mode", "Every screen uses the active appearance", "Cold start shows no opposite-appearance frame")
- [x] 4.2 Status area legible in dark: clock and status icons readable over the app background (spec: "Status area stays legible in the dark appearance")
- [x] 4.3 Device in light mode, cold start: everything light, status area legible (specs: "Launch on a device not in night mode")
- [x] 4.4 Change the device night mode with the app visible: the current screen adopts the new appearance (spec: "Night mode changes while the app is open")
- [x] 4.5 Change the device night mode with the app backgrounded, then return: new appearance without closing the app (spec: "Night mode changes while the app is backgrounded")
- [x] 4.6 With a video loaded and a trim range set, change the device night mode, return, and confirm the same video and range are intact and the preview still plays (spec: "Returning after a mid-trim appearance change")
- [x] 4.7 Look specifically at the `PlayerView` transport controls on Trim and Result in both appearances; report what you see even if it looks correct (design D5)
- [x] 4.8 Confirm no appearance control appears on any screen (spec: "No appearance control is offered")
- [x] 4.9 Cut a clip in dark mode and confirm the saved output's gallery date and GPS location are still preserved — appearance work must not touch the cut path
