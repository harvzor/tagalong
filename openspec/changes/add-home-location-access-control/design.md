## Context

See proposal.md — Why. Technical constraints shaping the approach:

- `HomeScreen.kt` today owns both launchers (`RequestPermission` chained before `OpenDocument`) and caches the grant result in `remember { mutableStateOf(checkSelfPermission(...)) }`, updated only by the launcher callback. A persistent status display must not go stale when the user grants/revokes from system settings.
- On Android 13+ the framework may stop showing the permission dialog after repeated denials; `requestPermissions` then resolves `false` with no UI. `shouldShowRequestPermissionRationale` distinguishes "declined, retry worth it" from "suppressed".
- The pick path itself (`pickVideo.launch(arrayOf("video/*"))` → `viewModel.onVideoPicked(uri)`) is unchanged; only the pre-pick permission branch and the post-denial `Text` are removed from it.
- `lifecycle-runtime-compose` is already an `:app` dependency, so resume-aware permission checks need no new dependency.
- Test surface: `E2eCutTest` is the only instrumented test that drives `HomeScreen`; it grants `ACCESS_MEDIA_LOCATION` via `GrantPermissionRule` and locates the button with exact-match `onNodeWithText("Pick video")`.

## Goals / Non-Goals

**Goals:**
- One file changed (`HomeScreen.kt`); no ViewModel, engine, manifest, dependency, or test-source changes.
- Permission state in the UI is derived from `checkSelfPermission` re-evaluated on lifecycle resume, not from launcher callbacks.
- The Enable button never produces a dead tap: request when the OS can show a dialog, app-settings intent when it cannot.

**Non-Goals:**
- No gating of "Pick video" on any permission (explored and deliberately rejected — denial degrades output, never blocks).
- No tappable granted-status line / in-app revocation path (user decision: revocation stays in system settings).
- No copy that disclaims app behavior ("never your current position" and similar phrasing deliberately excluded; the mechanism-only description suffices).
- No new instrumented tests for the grant/deny UI paths — the emulator auto-grants, matching the documented gap from the archived `add-access-media-location` design; manual device verification covers them.

## Decisions

### D1 — Permission state derives from a resume-lifecycle re-check

`checkSelfPermission` is evaluated in a `LaunchedEffect` keyed on the lifecycle resume event (via `LocalLifecycleOwner` + `LifecycleEventObserver`, available through `lifecycle-runtime-compose`). Launcher callbacks also update the state for immediate feedback.

*Alternative:* keep today's `remember` snapshot written only by the launcher — rejected: the persistent granted/off line must survive a Settings round-trip (spec scenario "Status reflects a system settings change").

### D2 — One Enable button, two behaviors, no separate "Open settings" button

Click handler: if not yet asked this session, or `shouldShowRequestPermissionRationale(activity)` is true → `requestPermission.launch(ACCESS_MEDIA_LOCATION)`. If asked once and rationale is now false (dialog suppressed / silently auto-denied) → `ACTION_APPLICATION_DETAILS_SETTINGS` intent with `package:` URI.

*Alternative:* always open settings — rejected: the first "yes" tap should produce the standard one-tap system dialog, not a settings maze. *Alternative:* always request — rejected: after lockout every tap would silently resolve `false` with no UI, indistinguishable from a broken button. The one-button form stays honest in both phases.

### D3 — Granted line is inert text

"📍 Media location access granted ✓" is a plain `Text` above the pick button. *Alternative:* tap-through to app settings for revocation — rejected by the user during exploration; keeps the surface minimal and the pick button the only action on screen.

### D4 — Status block placement: above "Pick video", always present

Off-state panel (heading + mechanism-only copy + Enable button) and on-state line both sit in the existing centred column, above the pick button, so the permission's effect is read *before* the primary action. Layout reuses the existing `Column`/`spacedBy` structure; no new scaffold.

### D5 — Copy states mechanisms, never disclaims behavior

Off copy: "GPS coordinates are stored inside your video files. Without permission to read them, Android strips GPS from every cut." No negative assurances ("never tracks you", "not your current position") — such denials plant fears and duplicate the Play-review prose, which stays in AGENTS.md/manifest/About.

### D6 — Delete rather than relocate the old warning

The red "GPS location may not be preserved" `Text` and the `RequestPermission` branch in `launchPick` are removed outright; the persistent panel is the replacement. *Alternative:* keep the warning on Trim/Result screens — rejected: app-level state belongs to one screen; per-cut warnings reintroduce the clutter this change removes.

## Risks / Trade-offs

**[Risk] Users who never tap Enable produce GPS-stripped cuts and may blame the app** — mitigated by the always-visible off panel + the cut-result ProbeCard surfacing the actual `location` field, so cause and effect are both on screen. This is the accepted trade-off of not gating.

**[Risk] `GrantPermissionRule` masks the off-state in instrumented tests** — `E2eCutTest` always sees the granted line. Accepted (documented gap since 2026-08-23); the off-state panel and settings fallback are verified manually on device. The test's exact-text match on "Pick video" remains unique because the new copy ("Enable media location access") does not contain that string.

**[Risk] OEM variance in how the silent-deny heuristic behaves** (`shouldShowRequestPermissionRationale` semantics differ slightly on some ROMs) — worst case a tap opens settings when a dialog was still possible, which is a recoverable wrong turn, not a dead end.

**[Risk] Status line reads as noise to users who intentionally run GPS-free workflows** — accepted; it is one short line, and the app's promise is metadata preservation.

## Migration Plan

Single-release UI change; no data, storage, or contract migration. Rollback = revert the commit. Existing users keep whatever grant state they already have; the status line simply reports it.

## Open Questions

_(none — the two candidate questions, granted-line tap-through and warning relocation, were decided during exploration: no and no.)_
