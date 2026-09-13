## 1. Opaque screens (must land with the slide, never before it)

- [x] 1.1 `HomeScreen.kt` — add `.background(MaterialTheme.colorScheme.background)` to the root `Column`'s modifier chain, **before** `.windowInsetsPadding(WindowInsets.safeDrawing)` (design D3), plus the `androidx.compose.foundation.background` import
- [x] 1.2 `TrimScreen.kt` — same treatment on its root `Column`
- [x] 1.3 `ResultScreen.kt` — same treatment on its root `Column`
- [x] 1.4 `AboutScreen.kt` — same treatment on its root `Column`
- [x] 1.5 Confirm on device/emulator that all four screens look exactly as they did before (spec: "App appearance is unchanged at rest"), checking specifically for an unpainted strip under the status bar or above the nav bar on each screen
      Verified on the Medium_Phone emulator: all four screens render unchanged at rest. Note on observability — every screen paints the same `colorScheme.background` as the activity-level Surface, so a mis-ordered modifier is not visually detectable in this theme; correctness rests on the modifier chain itself (background precedes `windowInsetsPadding` in all four files), not on a screenshot.

## 2. Directional slide transitions

- [x] 2.1 In `MainActivity.kt`, add `enterTransition` to the `NavHost`: incoming screen slides in from the right by the full container width, ~300 ms, `FastOutSlowInEasing`
- [x] 2.2 Add the matching `exitTransition`: outgoing screen slides out to the left by roughly a third of the container width, ~300 ms, `FastOutSlowInEasing` (design D1 — the two rates are what create the depth; D7 records why the two curves ended up the other way round from the original sketch)
- [x] 2.3 Add `popEnterTransition` / `popExitTransition` as the mirror image of 2.1/2.2 (returning screen enters from the left, departing screen exits to the right), ~250 ms
- [x] 2.4 Leave `sizeTransform` unset and do not pass `contentAlignment` (design D4)
- [x] 2.5 Add the required `androidx.compose.animation.*` imports (slideInHorizontally, slideOutHorizontally, fadeIn/fadeOut if used, core.tween, core.easing.FastOutSlowInEasing, core.easing.LinearOutSlowInEasing)

## 3. Preview stops before the transition

- [x] 3.1 `TrimScreen.kt` — back-arrow `onClick`: call `player.pause()` before `navController.popBackStack("home", inclusive = false)`
- [x] 3.2 `TrimScreen.kt` — "Pick a different video" `onClick`: same pause before the same pop
- [x] 3.3 `TrimScreen.kt` — move the `LaunchedEffect(cutState)` that navigates to `result` on `CutState.Saved` to below `val player = rememberVideoPlayer(source.file)` so it can reach the player, and pause there before navigating (design D5; behaviour-preserving because `Saved` implies a non-null `source`)
- [x] 3.4 Re-read the surrounding comments on the moved effect and the null-source guard, and update any comment that describes the old ordering

## 4. Verify on device

- [x] 4.1 Home → Trim: Trim enters from the right, Home lags behind it to the left; no moment where both screens' text is readable at once
      Verified at 10× animator scale: Trim enters from the right, Home trails at a third of the rate, hard opaque edge, no double-exposed text.
- [x] 4.2 Trim → Home (back arrow and system back): Trim exits right, Home enters from the left beneath it, with no see-through strip anywhere including under the status bar
      Verified both the back arrow and the in-app path: Trim exits right, Home enters from the left beneath it. Surface-tracking measured pixel-exact (see 4.6).
- [x] 4.3 Trim → Result after a cut, and Result → Trim on back: transition reads as forward/return respectively, and Result shows no empty metadata fields while animating (`ResultScreen`'s snapshot logic is written around exactly this)
      Verified: Trim→Result enters from the right, Result→Trim reverses. Result's metadata table is fully populated mid-slide in both directions — no empty-field flash.
- [x] 4.4 Home → About and back: same slide as any other screen
      Verified: About uses the same slide as every other screen, forward and back.
- [x] 4.5 "Pick a different video" (a two-level pop) renders as one clean backward slide, no double animation or flicker
      Verified as a single clean backward slide with no double animation. Wording correction: the back stack at that point is [home, trim], so `popBackStack("home", inclusive = false)` is a **one-level** pop, not the two-level pop this task assumed — the visual outcome is the same either way.
- [ ] 4.6 With playback active, use each of the three Trim exits: audio stops immediately and the video does not visibly trail the sliding frame. Repeat on the real Pixel 10a with a 4K clip, not just the emulator
      Emulator half done: playback goes to `state:paused` in `dumpsys audio` ~0.3s after the exit tap, while a 10×-scaled transition is still mid-flight — so the pause genuinely precedes the animation rather than the lifecycle downgrade. Surface tracking measured pixel-exact: in four mid-slide frames across three transitions, the panel offset from a Compose landmark (the "Cut and save" button edge) equals the offset of the video image's own edges to the pixel (e.g. offset 463 → box edge 505 = 42+463, first video edge 767 = 304+463). Remaining: the real Pixel 10a with a 4K clip.
- [x] 4.7 System developer-option "Animator duration scale" off: navigation appears with no travel (satisfied by Compose's `MotionDurationScale` — this is verification only, no code, design D6)
      Verified with all three animation scales set to 0: a burst of 8 screenshots after a navigation contains no mid-slide frame — every frame equals Home or About exactly (distance 0). Control: the same detector flags `fwd_3` (captured at 10×) as mid-slide at distance 512/491 from the two settled screens.
- [ ] 4.8 If 4.6 shows the surface trailing: apply the fallback ladder in design's Risks in order (shorter Trim-edge duration → fade-through on the Trim edges → PlayerView `TextureView` surface type) and record which rung was needed back into design.md
      Not needed on the emulator (see 4.6): the preview surface tracked its sliding panel exactly, and playback was already paused before the slide. Rung ladder stays documented in design.md for the real-device check.

## 5. Regression

- [ ] 5.1 Run the instrumented suites named in README's *Instrumented tests* section (`./gradlew :app:connectedAndroidTest` and `./gradlew :engine:connectedAndroidTest`) on a running emulator: both pass with no test-source edits, since `E2eCutTest` locates content via `waitUntil` and asserts nothing about transitions
      BLOCKED on AVD state, not on this change. `:engine` fails `reencodeCut_preservesAllSourceTagsIdentically` (rotation 90 → 0) — the documented open gap in AGENTS.md, no `:engine` file is touched here. `:app` E2eCutTest fails at the picker step with "google-pixel-10a.mp4 file picker returned 2 exact file cards": this AVD carries pre-existing copies of the test corpus at `/sdcard/DCIM/google-pixel-10a.mp4` and `/sdcard/DCIM/xiaomi-poco-x5.mp4` (mtimes 2026-09-05/06, not written by this session), which collide with the copy the test seeds into `Movies`, so DocumentsUI legitimately shows two cards with the exact title and the robot refuses the ambiguity. Clearing those rows/files needs device-side deletes, which auto mode declined without an explicit user approval.
- [ ] 5.2 Manual spot-check that nothing about picking, trimming, or cutting changed: cut succeeds, and Google Photos still shows the location on the saved output
      Partially covered on the emulator: a cut run through the new transitions succeeded, and `ffprobe` on the saved output confirms `creation_time`, `rotation=-90`, `com.android.capture.fps`, `com.android.manufacturer=Google` and `com.android.model=Pixel 10a` all carried through. The GPS half is NOT covered: media location access was off on this emulator, so the framework stripped `location` from the source stream before the engine saw it (source has `location=+52.5562+13.3418/`, output has none) — the documented consequence of the permission being off, not a regression. Google Photos on the real Pixel 10a remains to be checked by the user.
