## Context

Motivation is in proposal.md — Why. These are the technical facts that shape the approach, all verified against the resolved dependency sources rather than assumed.

- `MainActivity.kt` calls `NavHost(navController, startDestination = "home")` with **no** transition arguments, so navigation-compose 2.9.0's defaults apply. Its `NavHost.kt` declares `enterTransition = { fadeIn(tween(700)) }`, `exitTransition = { fadeOut(tween(700)) }`, and `popEnterTransition = enterTransition` / `popExitTransition = exitTransition`. Hence: a 700 ms crossfade, identical in both directions, and no way to tell forward from back.
- No screen draws a background. `grep` for `Scaffold|background(|Surface(` across `app/src/main` yields exactly one hit — the activity-level `Surface` in `MainActivity.kt`. Each screen is a bare `Column` with `windowInsetsPadding(WindowInsets.safeDrawing)`, resting on that shared surface.
- navigation-compose assigns z-order itself. `NavHost.kt` computes `targetZIndex` as `initialZIndex + 1f` when navigating forward and `initialZIndex - 1f` when popping. So the incoming screen rides **on top** going forward and sits **beneath** the departing screen going back.
- The outgoing screen's lifecycle is downgraded only after the animation finishes. `NavHost` calls `composeNavigator.onTransitionComplete(entry)` solely inside `if (transition.currentState == transition.targetState)`, which reaches `state.markTransitionComplete(entry)`. `VideoPreview.kt` pauses on `ON_STOP`, so playback outlives the start of any transition away from Trim.
- `VideoPreview.kt` renders media3's `PlayerView` through `AndroidView`. PlayerView's default surface type is a `SurfaceView` (added programmatically into `exo_content_frame`), which is a separate compositor surface and does not track View alpha or View translation the way an ordinary View does.
- Compose 1.11's `MotionDurationScale` multiplies every duration-based animation spec by the system animator scale; `scaleFactor == 0f` makes motion finish on the next frame callback. Reduced-motion handling therefore comes for free with any duration-based transition.
- Test surface: `E2eCutTest` finds content with `composeTestRule.waitUntil(...)` and `waitForIdle()`. Nothing asserts on the transition style or its duration.

## Goals / Non-Goals

**Goals:**
- Direction carries meaning: forward reads as entering, back reads as returning.
- The two screens in flight can never be read through each other.
- Total diff stays inside `:app`'s UI layer — no engine, ViewModel state, manifest, dependency, or Gradle changes.
- The change is invisible at rest: every screen looks exactly as it does today when nothing is animating.

**Non-Goals:**
- Shared-element / container-transform transitions from the "Cut and save" button into the Result screen. Beautiful, and wildly out of proportion to a four-screen utility.
- Predictive back gesture support. That needs Navigation 3's `NavDisplay`/`SceneStrategy`, which is not part of navigation 2.9.0 (`NavDisplay` does not exist in the resolved sources jar); adopting it is a nav-layer rewrite.
- Per-destination transition overrides. One global pair covers all four screens now that About is a peer.
- Replacing `SurfaceView` with `TextureView` as the primary approach — kept as a documented fallback only.
- Any change to the preview's transport controls, trim-range behaviour, or the `trim end` auto-stop rules.

## Decisions

### D1 — Parallax slide, not push and not slide-over

`enterTransition` translates the incoming screen the full screen width; `exitTransition` translates the outgoing screen roughly a third of it, in the same direction. Different rates is what produces the perception of depth.

*Alternative — 1:1 push (both screens move the same distance):* the user's own observation during exploration was that if both pages move together they never overlap, so the opaque-background work would be unnecessary. That is correct, and it was rejected precisely because a 1:1 push has no depth — it reads as one flat image being dragged, which is the look Android moved away from a decade ago. Choosing the version with depth is what makes the opaque background mandatory rather than optional (D3).

*Alternative — slide-over (new screen arrives, old screen frozen):* needs an edge shadow to read as a page rather than a sheet, and it makes every navigation look like a modal.

### D2 — One global transition pair on `NavHost`; About is a peer

Set `enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition` once on `NavHost` and leave the four `composable { }` blocks alone. Direction comes free: navigation-compose resolves the `pop*` pair for back navigation, so the mirroring is not hand-written.

About uses the same slide as every other screen — user decision during exploration ("About behaves as a peer page").

*Alternative — fade-through for the Trim edges (M3 fade-through), slide elsewhere:* considered because the Trim screen is the only screen containing a hardware surface. Rejected in favour of one consistent direction language plus D5's pause-before-navigate, which removes the condition that made sliding a video problematic. See Risks for the fallback if a device still shows the surface trailing.

### D3 — Opaque background on each screen's root, applied **before** the insets padding

Each screen's root `Column` gains `Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)` ahead of its existing `.windowInsetsPadding(WindowInsets.safeDrawing)` and `.padding(16.dp)`.

Order matters and is the trap in this change: a `background` applied *after* `windowInsetsPadding` paints only the inset-trimmed area, leaving an unpainted strip under the status bar / navigation bar that shows the other screen through it during a slide — reintroducing the exact ghosting this is meant to fix, in the least obvious place.

*Alternative — wrap each destination in a `Surface` or move screens onto `Scaffold`:* both give an opaque layer, but they introduce new layout structure (app bars, insets handling) into four files whose current shape is deliberate. A modifier is the same result at one line per file.

This satisfies the spec's "app appearance is unchanged at rest" scenario: the colour painted is the colour the screens already sit on.

### D4 — Keep `sizeTransform` null and `contentAlignment` at its default

A `SizeTransform` re-measures its content on every animation frame. Inside TrimScreen that means re-laying out the `AndroidView` hosting the `PlayerView` per frame, which is both expensive and the most likely source of visible tearing. `AnimatedContent`'s content alignment is left as `NavHost`'s default (`TopStart`), since every screen is `fillMaxSize` anyway.

### D5 — Pause the preview at the TrimScreen navigation call sites

TrimScreen's player is already a local `val player = rememberVideoPlayer(source.file)` inside the same composable that declares the back-arrow and "Pick a different video" controls, so pausing there needs no plumbing — each exit path calls `player.pause()` before handing control to `navController`.

The `Saved → result` navigation needs one structural change: that `LaunchedEffect` currently sits **above** `if (source == null) return` and therefore above the player declaration, so it cannot see `player`. It moves below the `val player` line. The move is behaviour-preserving — the `Saved` state implies a non-null `source`, so the effect's firing conditions are unchanged. This is what the `cut-workflow` delta's "Playback stops when the cut is saved" scenario pins down.

*Alternative — own the player in `CutViewModel`:* would make the player trivially reachable and pauseable from anywhere, but it moves creation and `release()` off the composition boundary that currently guarantees release when the screen goes away. Too invasive for a pause call.

*Alternative — rely on `ON_STOP`:* verified insufficient. `onTransitionComplete` only fires once `transition.currentState == transition.targetState`, so `ON_STOP` arrives after the animation and the surface is live and painting for the whole slide.

### D6 — Reduced motion needs no code

`MotionDurationScale` scales every duration-based spec by the system animator scale, and a `scaleFactor` of `0f` ends motion on the next frame callback. Because D1 and D7's transitions are duration-based (`tween`), the `screen-navigation` reduced-motion scenario is satisfied by Compose itself. The work is verification on device with animator duration scale set off, not a conditional branch.

*Alternative — read `LocalMotionDurationScale` and swap in `EnterTransition.None`:* duplicates framework behaviour, adds a branch that can drift out of sync with it, and would also silence the trim panel's expand/shrink animation, which is not this change's business.

### D7 — Easing curves are paired so the panels overlap on every frame

The panel that crosses the full width uses `LinearOutSlowInEasing` (decelerates into place); the depth panel behind it, travelling a third of the width, uses `FastOutSlowInEasing`. Applied to both directions, so the full-width traveller is the incoming screen going forward and the departing screen going back.

*Alternative — Material's conventional pairing (enter on `FastOutSlowIn`, exit on `LinearOutSlowIn`), which is what this design originally specified:* arithmetically broken for this geometry. `LinearOutSlowIn` leaves the origin steeply (≈5× the time axis near t=0) while `FastOutSlowIn` leaves it flat, so the depth panel's leftward drift temporarily outpaces the incoming panel's arrival. The incoming panel's leading edge then sits further right than the depth panel's trailing edge, exposing 5–8% of screen width — a `~25dp` column of bare background between two sliding panels, for roughly the first 60 ms of the transition. `FastOutSlowIn` for the depth panel also gives it a settle that matches the leading panel.

The check that matters, forward: with the incoming panel at `W(1-n)` and the depth panel's trailing edge at `W(1-e/3)`, no gap requires `n ≥ e/3` at every instant, and a decelerating leader satisfies it by construction. The back direction has `2/3 W` of slack in the same inequality, so it holds comfortably either way.

Verified on device rather than trusted to the algebra: emulator animator duration scale at 10× (a 3 s transition) with screenshots taken mid-slide.

## Risks / Trade-offs

**[Risk] The video surface still trails the sliding frame on real hardware** — pausing (D5) removes the *decoding* half of the problem but not the surface's one-to-two-frame lag behind its host View while translating. → Mitigation, in order of cost: shorten the Trim-edge duration; give the Trim edges fade-through instead of slide (D2's rejected alternative, still available because the transitions are one global pair plus per-destination overrides); set the PlayerView's surface type to `TextureView`, which animates in lockstep at the cost of some decode efficiency. Verified on the Pixel 10a, not only the emulator — a 320 dp preview of a 4K clip is the case that would show it.

**Measured on emulator, the risk above did not materialise.** With the preview paused before the transition (D5) and animator scale at 10×, the `SurfaceView`'s video content translates with its sliding panel to the pixel. Four mid-slide frames across three transitions: taking the panel offset from a Compose-drawn landmark (the "Cut and save" button's left edge, 43 at rest) and comparing it against the video image's own edges, the offsets are identical — e.g. offset 463 puts the surface box at 505 (42+463) and the first video edge at 767 (304+463); offset 234 → 276 and 538; offset 52 → 94 and 356. So neither mitigation rung below the pause is needed on the emulator. The ladder is kept live for the physical Pixel 10a with a 4K clip, which is the case the emulator corpus (11–16 MB) cannot exercise.

**[Risk] Audio now stops on paths where it previously kept playing until the screen went away** — the cut-saved navigation and the two Trim exits will go quiet a beat earlier. → Accepted, and specified: it is the `cut-workflow` delta, and silence during a page transition is the expected reading. Verified by the delta's three new scenarios.

**[Risk] A shorter transition changes the timing `ResultScreen`'s snapshot logic was written around** — that file deliberately retains its snapshot rather than clearing it, so the result "remains visible during the back-stack transition without flashing fallbacks". → Mitigation: that reasoning is duration-independent, but the back-from-Result path is an explicit manual check for empty-field flashing after the change.

**[Risk] The background modifier is applied in the wrong order on one of the four screens** → Mitigation: the failure mode is visible on device as a ghosting strip under the status bar, and each of the four screens is a named verification task rather than a collective "transitions look good".

**[Risk] Instrumented tests become timing-sensitive** — Compose test automation idles animations, and `E2eCutTest` matches text via `waitUntil`, so neither the style nor the duration is asserted. → Accepted; if a device run turns up an interaction landing mid-animation, the correct fix is an idle wait in the test robot, not a longer transition.

## Migration Plan

Single commit inside `:app`. Rollback is reverting it; nothing is persisted, no state shape changes, no migration. Ship order is trivially safe because the transition configuration and the opaque backgrounds must land together — landing the slide without the backgrounds is a visible regression, so the revert must be atomic too.
