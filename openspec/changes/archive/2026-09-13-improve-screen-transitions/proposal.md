## Why

Screen changes in the app are currently a bare `NavHost` with no transition arguments, so navigation-compose 2.9.0's defaults apply: a **700 ms pure crossfade, identical in both directions**. Because no screen paints its own background — all four draw directly onto the single activity-level `Surface` in `MainActivity.kt` — the outgoing and incoming screens are composited on top of each other at partial alpha for the whole animation, so every label is double-exposed. Users read this as the app stuttering rather than navigating, and there is no directional cue distinguishing "going deeper" from "going back".

## What Changes

- **Directional slide replaces the crossfade.** Forward navigation slides the new screen in from the right while the old screen slides out at a slower rate (parallax, which is what gives the motion depth); back navigation mirrors it. Durations land in the 250–350 ms range with Material easing instead of 700 ms of flat fading.
- **Every screen gets an opaque background.** Required, not cosmetic: a parallax slide deliberately overlaps the two screens, and on back navigation the incoming screen is composited *beneath* the departing one. A transparent page in either position reproduces today's ghosting, just moving. Visually identical to the current app in normal use, because it is the same colour the screens already sit on.
- **Preview playback stops when leaving the Trim screen.** The outgoing screen's lifecycle is only downgraded after the transition completes, so a playing clip currently keeps decoding while its `SurfaceView` is translating across the screen, and the picture trails the frame. Pausing at the navigation call site puts the pause ahead of the animation.
- **System reduced-motion is respected.** When animation scale is off, navigation resolves to a plain near-instant transition instead of a slide.
- All four screens, including About, use the same slide (decision taken during exploration: About behaves as a peer page, not as a modal panel).
- No new dependencies; no change to picking, trimming, cutting, or metadata behaviour.

## Capabilities

### New Capabilities

- `screen-navigation`: How the app's screens animate between each other — direction, timing, opacity, and reduced-motion behaviour, plus the requirement that each screen presents an opaque surface so two screens can never be seen through one another.

### Modified Capabilities

- `cut-workflow`: "User can play the video with audio" gains a condition — leaving the Trim screen stops playback before the screen transition begins, rather than only when the screen's lifecycle is later downgraded.

## Impact

- `app/src/main/java/dev/tagalong/app/MainActivity.kt` — `NavHost` gains enter/exit/pop-enter/pop-exit transition lambdas
- `HomeScreen.kt`, `TrimScreen.kt`, `ResultScreen.kt`, `AboutScreen.kt` — opaque background on each screen's root
- `TrimScreen.kt` — its navigation exits (back arrow, "Pick a different video", and the system-back path) pause the preview player first
- `VideoPreview.kt` — the player instance must be reachable from those call sites; how it is created and released does not change
- No dependency, manifest, engine, or Gradle changes
- `E2eCutTest` is unaffected: it locates content with `waitUntil`, which does not depend on transition style or duration
