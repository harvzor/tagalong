## Context

TrimScreen currently has three exit routes with three different shapes:

- **Back arrow** → `player.pause()` then `popBackStack("home", inclusive = false)`
- **"Pick a different video"** → identical to the arrow
- **System back gesture** → NavHost's default pop, **no `player.pause()`**

`rememberVideoPlayer` pauses only on `Lifecycle.Event.ON_STOP`, which — per the archived `improve-screen-transitions` change — fires only after the slide animation completes. That is why the two visible controls pause eagerly at their call sites. The gesture path never got that treatment, so it is the one route that still shows a playing `SurfaceView` trailing across the screen.

On a share launch, Trim is the start destination and Home is never on the back stack, so the visible controls' explicit `popBackStack("home", …)` cannot find a target and silently no-ops. The gesture path, by contrast, falls through to the default start-destination handling and finishes the Activity — which is exactly what `home-screen`'s "Back navigation from Trim returns to Home" requires for a share-launched Trim ("the app exits and the sending app is shown"). So today the gesture is spec-correct on a share launch and the visible arrow is not.

`ResultScreen` already demonstrates the target pattern: a single `onBack` lambda wired to both a `BackHandler` (for the gesture) and the visible arrow's `onClick`.

## Goals / Non-Goals

**Goals:**
- One Trim exit handler; every route pauses the preview before its transition begins.
- The back arrow reaches its correct destination in both launch modes — Home for an in-app pick, exit-to-sender for a share.
- One visible back affordance instead of two identical ones.
- Arrow left edge aligns with the content beneath it.

**Non-Goals:**
- Not restyling the Trim/Result top bars into a Material3 `TopAppBar`.
- Not changing the launch-dependent `startDestination` design (deliberate in `add-share-target`; it prevents a Home flash on a share launch).
- Not touching the share intake/probe engine or ResultScreen behavior.
- No new instrumented test for the gesture-stop case (user decision).

## Decisions

### D1 — Collapse to a single `onBack`, wired to the arrow and a `BackHandler`

One lambda is the sole Trim exit: pause first, then leave. `BackHandler(onBack = onBack)` covers the gesture; the arrow's `onClick = onBack` covers the tap. A `BackHandler` declared inside `TrimScreen` is only active while that destination is composed, so it cannot leak into Home or Result. This mirrors ResultScreen's existing pattern rather than inventing a new one.

### D2 — The handler pops, and finishes only when there is nothing to pop

```kotlin
val onBack: () -> Unit = {
    player.pause()
    if (!navController.popBackStack()) {
        activity?.finish()      // at start destination (share launch) → exit to sender
    }
}
```

- **In-app pick** (`home → trim`): `popBackStack()` pops Trim, returns `true`, Home is revealed.
- **Share launch** (Trim is the sole entry): `popBackStack()` cannot pop the start destination, returns `false`, so the handler finishes the Activity → back to the sender.

**The non-obvious part:** naively wiring `BackHandler { navController.popBackStack() }` looks correct but *regresses* the share case. A `BackHandler` consumes the event, so at a share root the pop does nothing, the callback still swallows the gesture, and the default start-destination handling that used to finish the Activity never runs — the gesture would stop exiting to the sender. The explicit `finish()` fallback preserves exactly the outcome that path relies on.

The activity is obtained via the local composition context (`LocalContext.current as? Activity`); no new plumbing.

### D3 — Left-align with a negative offset, not a `TopAppBar`

The arrow sits ~12dp right of the content below it: the screen's `Column` already applies `.padding(16.dp)`, and `IconButton` centers its 24dp glyph inside a 48dp touch target (~12dp of its own inset). A `Modifier.offset(x = (-12).dp)` on the arrow's `IconButton` brings the glyph to the 16dp content edge.

A Material3 `SmallTopAppBar` would handle insets per spec, but it applies its **own** `safeDrawing` top inset, and these screens' root `Column` already calls `.windowInsetsPadding(WindowInsets.safeDrawing)` — nesting them yields a **double top inset**. Avoiding that would mean restructuring the root of both Trim and Result for a 12dp visual fix. Rejected as heavier and riskier than the offset.

The same offset is applied to ResultScreen's arrow so the two hand-rolled top bars stay visually consistent.

## Risks / Trade-offs

- **Over-consuming the system gesture** — the `finish()` fallback in D2 is the guard; without it the share exit-to-sender regresses. This is the single highest-attention item for the implementer.
- **E2eCutTest anchor** — `returnToHome()` detects the Trim screen and clicks by the `"Pick a different video"` text, which no longer exists. It must click the arrow by its `"Back to home"` content description instead. The unrelated ResultScreen `"Back to trim"` path is untouched.
- **Finishing mid-animation** — only the share-root branch calls `finish()`; there is no incoming screen to composite against, so there is no trailing-SurfaceView exposure on that branch.
- **Predictive back (API 36 target)** — wiring through `BackHandler` keeps Trim compatible with the system back dispatcher that `MainActivity` relies on for share re-delivery; no predictive-back polish is introduced or removed beyond this handler.
