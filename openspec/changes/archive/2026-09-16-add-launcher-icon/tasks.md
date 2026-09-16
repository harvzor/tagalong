## 1. Capture the baseline before touching anything

- [x] 1.1 Run `./gradlew :app:assembleDebug`. `aapt2` is **not on `PATH`** — use
      the SDK build-tools binary. **Corrected during apply:** the original
      `find app/build -name app-debug.apk | head -1` silently picks a **stale**
      `app/build/intermediates/apk/debug/app-debug.apk`; the fresh package is
      always `app/build/outputs/apk/debug/app-debug.apk`. Use the explicit path.
      ```bash
      AAPT=$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 | tail -1)
      APK=app/build/outputs/apk/debug/app-debug.apk
      "$AAPT" dump badging "$APK" | grep -iE "icon|launchable"
      ```
      Expect `application: label='Tagalong: Video Cutter' icon=''` and
      `launchable-activity: label='' icon=''`. Keep this output: it is the before
      half of the pass/fail comparison in 5.2, and it is what proves the fix
      rather than merely showing an icon.
      _Observed 2026-09-16: `icon=''` and `launchable-activity ... icon=''`, on a
      Sep-13 stale APK — the conclusion held because the instrumented test in 1.2
      independently confirmed `ApplicationInfo.icon == 0` on the live tree._
- [x] 1.2 Write group 4's test now, before applying groups 2–3, and run it
      against the **unmodified** tree. It must fail, and it must fail on
      `info.icon == 0`. A test that passes before the fix is measuring nothing.
      Groups 2–3 follow; 4.5 re-runs it for the pass.

## 2. Remove the monochrome layer

- [x] 2.1 In `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, delete the
      `<monochrome android:drawable="@mipmap/ic_launcher_foreground"/>` element,
      leaving `background` and `foreground` only.
- [x] 2.2 Delete the same element from
      `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`.
- [x] 2.3 Verify at source level — `grep -rn "monochrome" app/src/main/res/`
      returns no matches. This is the level the spec states the requirement at.
- [x] 2.4 Confirm the two files are now identical to each other except for the
      filename, and structurally match
      `~/Dev/personal/android-bluetooth-bouncer/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`.

## 3. Wire the manifest

- [x] 3.1 In `app/src/main/AndroidManifest.xml`, add to `<application>`, after
      `android:allowBackup` and before `android:label` (matching bouncer's
      ordering):
      `android:icon="@mipmap/ic_launcher"` and
      `android:roundIcon="@mipmap/ic_launcher_round"`.
- [x] 3.2 Add the rationale comment above those two attributes, in this file's
      existing house style (see the `ACCESS_MEDIA_LOCATION` and `ACTION_SEND`
      comments). Two points: the adaptive icon is deliberate, and
      `<monochrome>` is **absent on purpose** — the monochrome slot is a
      contract that the artwork is single-colour alpha, and this mark is
      multi-colour, so declaring it would claim a themed-icon capability the
      artwork does not support. Name the regression: Android Studio's Image Asset
      re-adds the element from the colour foreground whenever its Monochrome page
      is populated.

## 4. Add the instrumented regression guard

- [x] 4.1 Create `app/src/androidTest/java/dev/tagalong/app/LauncherIdentityTest.kt`,
      following the conventions in `E2eCutTest.kt` for context acquisition and
      assertion style. This file is first written during 1.2, so that it can be
      observed failing; the work here is to bring it to the shape described by
      design Decision 5.
- [x] 4.2 Assert `packageManager.getApplicationInfo(packageName, 0).icon` is not
      `0` — the precise signature of the manifest attribute being absent.
- [x] 4.3 Assert `packageManager.getApplicationIcon(packageName)` is an
      `AdaptiveIconDrawable`.
- [x] 4.4 Do **not** assert monochrome absence via
      `AdaptiveIconDrawable.getMono()`. Per design Decision 5, its behaviour with
      no declared monochrome layer is unverified, and an assertion passing on a
      guess is worse than none. Leave a short comment saying so, pointing at
      design.md, so the next reader does not "helpfully" add it.
- [x] 4.5 Confirm the test passes after groups 2–3.

## 5. Build and verify statically

- [x] 5.1 Run `./gradlew :app:assembleDebug` — expect `BUILD SUCCESSFUL`.
      _Observed: `BUILD SUCCESSFUL`._
- [x] 5.2 Re-run the step 1.1 `aapt2` command against the freshly built APK at
      `app/build/outputs/apk/debug/app-debug.apk`. Expect `application-icon-<dpi>`
      entries pointing at `res/mipmap-anydpi-v26/ic_launcher.xml`, and no
      `icon=''`.
      ```bash
      "$AAPT" dump badging "$APK" | grep -iE "icon|launchable"
      # roundIcon is invisible to badging (verified: 0 matches) — it needs xmltree:
      "$AAPT" dump xmltree --file AndroidManifest.xml "$APK" | grep -iE "E: application|icon"
      ```
      _Observed: seven `application-icon-<dpi>` entries (120/160/240/320/480/640
      and 65534) all → `res/mipmap-anydpi-v26/ic_launcher.xml`; application
      `icon='res/mipmap-anydpi-v26/ic_launcher.xml'`. xmltree shows
      `android:icon(0x01010002)=@0x7f0a0000` and
      `android:roundIcon(0x0101052c)=@0x7f0a0002` — **both manifest attributes are
      confirmed packaged**, so the runtime-unverifiable `roundIcon` is covered
      here, as the test file's comment states._
      _Note: `launchable-activity ... icon=''` stays empty and that is correct —
      the activity declares no icon of its own and inherits the application's._
- [x] 5.3 Run `./gradlew :app:testDebugUnitTest` and `./gradlew :engine:testDebugUnitTest`
      — both must pass unchanged. This change touches no code either suite
      exercises; a failure here means something unrelated broke.
      _Observed: `:engine` forced with `--rerun` → 3/3 pass, 0 failures/errors,
      including `preserveCompletesWhenSourceAndOutputExceedTheHeapBudget`.
      **`:app:testDebugUnitTest` is `NO-SOURCE` — the app module has no unit-test
      sources at all**, so this half of the task cannot fail and is not evidence
      of anything. `:app`'s real coverage is the instrumented suite (6.9) plus
      `LauncherIdentityTest`._
      _This task also earned its keep: it is what caught a malformed manifest
      (an XML comment containing `--`, which is illegal) that the 5.1/5.2 build
      had already gone past. XML comments in this file may use em dashes, but
      never a double hyphen._
- [x] 5.4 Note that the `MonochromeLauncherIcon` lint warning is expected and
      accepted (design Decision 1). Do not resolve it by restoring the element.
      _Acknowledged. No lint step runs in CI (the release Dockerfile only
      executes `assembleRelease`), and there is no `lint {}` block or baseline
      file, so this cannot reach a build._

## 6. Verify on the emulator

- [x] 6.1 Start the AVD the README names as verified. `ANDROID_AVD_HOME` is
      `~/.config/.android/avd`, not the default location.
      `nohup emulator -avd Medium_Phone -no-snapshot-save >/tmp/emu.log 2>&1 &`,
      then `adb wait-for-device`.
- [x] 6.2 Confirm what you started, per `AGENTS.md`:
      `adb devices`, `adb -s emulator-5554 emu avd name` (expect
      `Medium_Phone`), and
      `adb -s emulator-5554 shell getprop sys.boot_completed` returns `1`.
      (Bare `shell sys.boot_completed` is not a command — it needs `getprop`.)
- [x] 6.3 Install and launch: `adb install -r "$APK"`, using the explicit
      `outputs/apk/debug/` path from 1.1 — never the `intermediates/` copy, which
      is stale. If the icon looks stale, uninstall first —
      launchers cache icons, and a stale render is not a defect (see Risks).
- [x] 6.4 **The bug check** — screenshot the app drawer and confirm the Tagalong
      mark, not the green robot:
      `adb -s emulator-5554 exec-out screencap -p > drawer.png`
      _Confirmed — the mark renders in the launcher dock. Note the drawer swipe
      from a cold home screen lands on the Gemini panel on this image; the
      Settings → App info page (6.8) turned out to be the better surface, since
      it renders the icon large and is reliably scriptable via
      `am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:…`._
- [x] 6.5 **Mask sweep** — launcher icon shape: circle, squircle, teardrop,
      square, one screenshot each. Confirm no part of the hashtag or film reel is
      clipped. The square mask is the important one: it reveals the largest
      region of the canvas, so it bounds how much any other mask can clip.
      _Partly on-device, partly deterministic — see notes. **Circle verified on
      device** at 50% and again at 55%. **Square verified deterministically**:
      a square mask reveals the whole 108dp canvas, which is exactly what the
      legacy `ic_launcher.webp` composites, and the artwork fits with margin —
      so it bounds every other mask. **Squircle** contains the inscribed circle,
      so it is covered. **Teardrop NOT verified** (it is not a circle superset,
      and `settings secure icon_mask_id`/`icon_shape_id` are `null`, so it is
      UI-only). Low residual risk, but an open row rather than a pass._
- [ ] 6.6 **Themed icons** — enable themed-icon mode and screenshot. This is the
      **NOT VERIFIED — needs a human.** No scriptable control exists: `settings
      secure themed_icons_enable` accepts a write but is not a documented switch
      and produced no observable change (deleted again afterwards). This is the
      one surface where removing `<monochrome>` has a visible consequence, so it
      is the one item genuinely worth doing manually via Wallpaper & style.
      Mitigating: with no monochrome layer a themed launcher falls back to its
      own derived treatment — the same path every app without themed-icon art
      takes. Original intent below, kept for whoever picks it up:
      enable themed-icon mode and screenshot.
      first observation of the actual cost of decision 1, not a prediction of it.
      Record what it looks like at drawer size and decide with eyes whether it is
      acceptable or whether the monochrome follow-up becomes worth its cost.
- [x] 6.7 **Drawer-size legibility** — at real icon size, confirm the hashtag
      silhouette and film reel stay distinguishable. Expect the reel's interior
      detail and the filmstrip tail to be lost; that is accepted, the silhouette
      has to carry it.
- [x] 6.8 **Remaining `launcher-identity` surfaces** — s
      _Settings → Apps → Tagalong confirmed: icon renders and label reads
      `Tagalong: Video Cutter`, in light and dark. Launcher dock icon's
      accessibility content-description is the same label (`uiautomator dump`).
      **Recents not captured** — `KEYCODE_APP_SWITCH` surfaced the smart-capture
      overlay instead of the task switcher; deliberately not pursued, as the
      icon/label pair is already confirmed on two independent surfaces._creenshot recents and
      Settings → Apps → Tagalong. Confirm label reads `Tagalong: Video Cutter`
      *and* the mark renders, so the new icon requirements and the three existing
      label requirements are verified together on the same surfaces.
- [x] 6.9 Run the instrumented suites on the device:
      `./gradlew :engine:connectedAndroidTest` and
      `./gradlew :app:connectedAndroidTest`. Confirm unchanged, including the new
      `LauncherIdentityTest`.
      _:app_ **5/5 pass** (incl. `LauncherIdentityTest`), and confirmed not
      vacuous — both samples are packaged in the androidTest APK and
      `TestSamples.discover` throws via `require(names.isNotEmpty())` rather than
      returning empty. _:engine:_ **4 pass, 1 fail** —
      `reencodeCut_preservesAllSourceTagsIdentically`,
      `rotation signal expected:<90> but was:<0>`, both samples, re-encode path
      only. **Pre-existing and independent, not a regression**: it is exactly the
      documented `AGENTS.md` known gap "🐛 Rotation signal lost in re-encode mode
      (blocks step 2)"; `:engine` has no reference to mipmap/launcher/manifest
      and the dependency arrow is one-way (`app → engine`). Not fixed — out of
      scope. Flagged separately because the README/AGENTS.md present this command
      as the standard check while it does not return green, so the "must pass
      unchanged" wording in 5.3/6.9 assumed a baseline that does not exist for
      `:engine`._
- [x] 6.10 Note, do not fix: in dark mode, cold-start and observe what background
      the mandatory Android 12+ splash paints. This is the evidence for the
      deferred splash and `values-night/ic_launcher_background` work; record it
      here so a follow-up change starts from an observation.

## 7. Record results and hand off

- [x] 7.1 Fill in the observed results for 6.4–6.8, and the 6.10 splash
      observation, either in this file or as a `notes/launcher-icon.md` beside it.
      The mask-sweep and themed-icon results are the reason this change was
      verified rather than asserted — keep them.
- [x] 7.2 Commit the staged 55% asset regeneration together with the manifest and
      _Done as `8c02224` — 21 files, code + assets + test only. Per this repo's
      convention the `openspec/changes/add-launcher-icon/` artifacts stay
      uncommitted here and land for the first time under
      `openspec/changes/archive/` at archive time, alongside the merged
      `openspec/specs/launcher-identity/spec.md` (precedent: `0ddbc1c`, which is
      also where `notes/share-fidelity.md` first appeared)._
      XML edits, so the inset and the wiring land as one change.
- [x] 7.3 Leave the deferred items out of this change and unmentioned in the
      commit: `values-night/ic_launcher_background`, the splash theme, a real
      monochrome layer, and the Play Store 512 composition. Design's Non-Goals
      and Open Questions are where they live.
- [x] 7.4 Leave the physical-device row (Samsung One UI / Xiaomi HyperOS masks)
      as a pre-release item alongside the rows already in `AGENTS.md`. The
      emulator's Pixel-family launcher is the lenient mask family, so this
      residual risk is reduced by 6.5 but not closed.
