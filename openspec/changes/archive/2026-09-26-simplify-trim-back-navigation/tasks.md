# Tasks

## 1. Collapse the Trim back handler

- [x] 1.1 `TrimScreen.kt` — add a single `val onBack: () -> Unit = { player.pause(); if (!navController.popBackStack()) activity?.finish() }`, taking `activity` from `LocalContext.current as? Activity` (design D2). Comment why `finish()` is required at a share root: a `BackHandler` that only popped would consume the gesture and never run the default start-destination exit.
- [x] 1.2 `TrimScreen.kt` — point the top-left `IconButton`'s `onClick` at `onBack` (replacing its inline `pause()` + `popBackStack("home", …)`).
- [x] 1.3 `TrimScreen.kt` — import `androidx.activity.compose.BackHandler` and add `BackHandler(onBack = onBack)`, matching the pattern already used in `ResultScreen.kt` (design D1).

## 2. Remove the redundant button

- [x] 2.1 `TrimScreen.kt` — delete the `"Pick a different video"` `OutlinedButton` block (the exit route it shared is now the sole `onBack`).
- [x] 2.2 `TrimScreen.kt` — drop the now-unused `OutlinedButton` import if nothing else in the file uses it.

## 3. Left-align the back arrows

- [x] 3.1 `TrimScreen.kt` — add `Modifier.offset(x = (-12).dp)` to the arrow's `IconButton` so the glyph's left edge meets the 16dp content edge (design D3). Confirm the arrow visually aligns with the path label and the `Cut and save` button beneath it.
- [x] 3.2 `ResultScreen.kt` — apply the same offset to that screen's arrow `IconButton` for visual consistency. No behavioural change to its `onBack`.

## 4. Keep the E2E suite green

- [x] 4.1 `E2eCutTest.kt` — `returnToHome()` currently waits for and clicks the `"Pick a different video"` text, which is gone. Retarget it to detect the Trim screen by its `"Cut and save"` label and click the arrow by its `"Back to home"` content description. Leave the `"Cut result"` → `"Back to trim"` branch as-is.
  - Added a `hasComposeContentDescription` / `waitForComposeContentDescription` helper pair alongside the existing text helpers, and imported `onAllNodesWithContentDescription`.

## 5. Verify against the specs

> Verified on `Medium_Phone` (emulator-5554, API 37, density 420) after the emulator was
> wiped and re-provisioned. The earlier failures were a degraded emulator instance: once
> restarted, the DocumentsUI flow drove normally and every check below ran against the real UI.

- [x] 5.1 Manual, in-app pick: start a playing clip, press back via **both** the arrow and the system gesture — playback stops before the slide, Home appears.
  - **Verified on-device, both routes.** Preview playback confirmed `started` via `dumpsys audio` (`AudioPlaybackConfiguration` for the app's uid/pid), then observed `paused` **20–30 ms** after leaving Trim — by the visible arrow *and* by `KEYCODE_BACK`. Reproduced across three runs.
  - Control: this clip's trim range spans the whole 6.5 s duration, so the trim-end auto-stop is the only other stop source. Playback was observed stopping at **0.09–0.11 s**, so it cannot be attributed to that guard — the pause comes from `onBack`.
  - Destination was Home on both routes ("Pick video" present, "Cut and save" absent). **This is the gap the change closes**: the system gesture previously had no pause call site at all and relied on the late `ON_STOP`.
- [x] 5.2 Manual, share launch: share a video into Tagalong, start a playing clip, press back via both routes — playback stops and the app exits to the sender, never Home.
  - **Confirmed manually by the user: back from a share-launched Trim returns to the sender.** Home is not shown, matching `home-screen`'s existing "Back from Trim after a share" scenario.
  - Why this needed a human: the share *intake* half is already covered by the passing `ShareIntakeTest.sharedVideo_opensTrimDirectlyWithoutHome`, which establishes the Trim-rooted back-stack shape this check depends on. But the exit half cannot be driven from `adb` — `parseSharedVideo` treats `contentResolver.getType(uri)` as authoritative when it returns non-null, and the app deliberately declares no `READ_MEDIA_VIDEO`, so an adb-forged MediaStore URI resolves to a non-`video/*` type, the `?: intent.type` fallback never fires, and the app correctly falls back to Home. Forging a representative sender would require granting a permission the app intentionally omits, which would not be a faithful test either way.
  - This confirms design D2's `finish()` fallback — without it, a `BackHandler` that only popped would have consumed the gesture at a share root and broken exactly this behaviour. Naive implementation rejected on those grounds during design, and the regression is confirmed absent.
- [x] 5.3 Manual: confirm the Trim and Result back arrows sit flush-left with the text beneath them.
  - **TrimScreen verified numerically via `uiautomator` node bounds** at density 420 (16dp = 42px, 12dp offset ≈ 31.5px): arrow glyph left edge **43px** vs the 16dp content edge **42px** — **1px apart**, i.e. flush. Pre-fix the glyph sat ~31px further right.
  - **ResultScreen: code applied, not device-verified.** Identical one-line `Modifier.offset(x = (-12).dp)` as 3.1, but reaching Result requires a full cut, which was not completed this session. Low risk, worth a glance during release verification.
- [x] 5.4 Run `./gradlew :app:connectedAndroidTest` — full `E2eCutTest` passes with the retargeted `returnToHome()`.
  - **`E2eCutTest` passes** — `tests=1 failures=0 errors=0 skipped=0` (16.2 s), exercising the retargeted `returnToHome()` against the real corpus, clicking the arrow by its `"Back to home"` content description.
  - **Full `:app` connected suite green — 5/5**, so no regression in the neighbouring `ShareIntakeTest` / `LauncherIdentityTest`.
  - Compiles clean: `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin`; only the two pre-existing `createAndroidComposeRule` / `createEmptyComposeRule` deprecation warnings appear.

## 6. Sync specs at archive time

- [x] 6.1 Confirmed the two deltas match the shipped behavior:
  - `cut-workflow` — back-arrow / picking-a-different-video / system-gesture / cut-saved playback-stop routes each map to a distinct `player.pause()` call site ahead of navigation (`onBack` for the first three; the existing `LaunchedEffect(cutState)` for the cut).
  - `screen-navigation` — the back control performs a plain `popBackStack()`, which returns true and leaves Home as the sole entry on an in-app-pick stack.
  - Share-launch exit-to-sender is preserved by the `finish()` fallback, matching `home-screen`'s existing "Back from Trim after a share" scenario (no delta needed there — the code now complies with the spec that already ships).
