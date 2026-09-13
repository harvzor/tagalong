# Tasks

## 1. Change the launcher label

- [x] 1.1 In `app/src/main/res/values/strings.xml`, change `app_name` from
      `Tagalong` to `Tagalong: Video Cutter` (keep the existing comment/style of
      the file; no other resource changes).
- [x] 1.2 Confirm no other resource or manifest edit is needed:
      `AndroidManifest.xml` keeps `android:label="@string/app_name"` on
      `<application>` and gains no activity-level `android:label`.

## 2. Confirm in-app branding is untouched

- [x] 2.1 Verify `HomeScreen.kt` brand text still renders `Tagalong`.
- [x] 2.2 Verify `AboutScreen.kt` still renders `Tagalong` (app name line and
      license line unchanged).
- [x] 2.3 Verify no test asserts on the launcher label (grep `:app` / `:engine`
      test sources for `app_name` / application-label checks); confirm none
      exists, so no test edits are required.

## 3. Build and verify

- [x] 3.1 `./gradlew :app:assembleDebug` succeeds.
- [x] 3.2 Install a debug build over an existing install on a device/emulator
      and confirm it is treated as an update (same `dev.tagalong.app`), not a
      duplicate app.
- [x] 3.3 On the device, confirm the app drawer and home-screen caption read
      `Tagalong: Video Cutter`, and the recents/task-switcher title and
      Settings → Apps entry match.
- [x] 3.4 Launch the app and confirm the Home screen and About screen still
      show `Tagalong`.
- [x] 3.5 Cut a clip and confirm the output still lands in `Movies/Tagalong/`.
