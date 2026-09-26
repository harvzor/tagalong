## Why

The Trim screen shows two controls — a top-left back arrow and a "Pick a different video" button — whose click handlers are byte-for-byte identical (`player.pause()` then pop to Home). One of them is pure duplication. Worse, the two visible controls are the only exits that pause the preview, so the **system back gesture bypasses the pause** and the still-playing `SurfaceView` trails across the slide — the exact bug the `improve-screen-transitions` change documented and set out to fix, but only wired into the visible controls. And on a share-launched session, both visible controls call `popBackStack("home")` against a stack that has no Home, so the back arrow silently does nothing while the system gesture exits to the sender.

## What Changes

- **Remove the "Pick a different video" button.** The top-left back arrow is the single visible exit on the Trim screen.
- **Route every Trim exit through one pause-then-leave handler.** The back arrow, the system back gesture, and the navigation already wired to a successful cut all pause the preview *before* the transition begins — closing the system-back playback-stop gap ResultScreen already closed via its own `BackHandler`.
- **Fix the Trim back action to reach its destination in both launch modes.** A single pop that lands on Home when Home is on the stack (in-app pick) and exits to the sending app when it is not (share launch). This is a **bug fix toward the existing spec**, not a behavior change: `home-screen`'s "Back navigation from Trim returns to Home" already specifies exit-to-sender for a share-launched Trim; today's code just fails to perform it.
- **Left-align the Trim back arrow** with the content beneath it. The arrow currently sits ~12dp right of the path label and buttons because `IconButton` adds its own internal inset on top of the screen's 16dp padding. Same nudge applied to ResultScreen's arrow for consistency.
- No change to picking, trimming, cutting, metadata, or any animation timing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cut-workflow`: "User can play the video with audio" — the exit-pause scenarios are retargeted off the removed button and extended so the system back gesture is covered alongside the back control.
- `screen-navigation`: "Transitions do not alter navigation outcomes" — the "Multi-level back is unchanged" scenario is retargeted from the removed "Pick a different video" label to the Trim screen's back control.

## Impact

- `app/src/main/java/dev/tagalong/app/TrimScreen.kt` — remove the `OutlinedButton`; add a single `onBack` handler wired to both the arrow and a `BackHandler`; replace the `popBackStack("home", …)` exit with a pop that reaches Home or exits at stack root; nudge the arrow left.
- `app/src/main/java/dev/tagalong/app/ResultScreen.kt` — matching arrow left-alignment only.
- `openspec/specs/cut-workflow/spec.md` and `openspec/specs/screen-navigation/spec.md` — the two scenario retargets described above (captured as a delta in this change).
- `app/src/androidTest/java/dev/tagalong/app/E2eCutTest.kt` — `returnToHome()` clicks the arrow by its `"Back to home"` content description instead of the removed button's text.
- No dependency, manifest, engine, or Gradle changes.
