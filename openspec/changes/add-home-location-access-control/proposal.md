## Why

Today the app requests `ACCESS_MEDIA_LOCATION` lazily on the "Pick video" tap, immediately before the system file picker launches. On modern Android (16+/API 37 verified on the Medium_Phone emulator) the picker then shows its own media-consent dialog, so a first-time user gets two unrelated permission dialogs back-to-back from a single tap — and neither one explains why a video trimmer is asking about location. The GPS-preservation promise is the app's core product, so the permission's state deserves a clear, always-visible home in the UI rather than a transient red warning after a denial.

## What Changes

- The Home screen gains a persistent **media location access** block **above** the "Pick video" button:
  - When not granted: a heading, a mechanism-only explanation (GPS lives inside the video file; without read permission Android strips it from cuts — no behavior-disclaimer copy), and an **"Enable media location access"** button that owns the `ACCESS_MEDIA_LOCATION` runtime request.
  - When granted: an inert status line "📍 Media location access granted ✓" (not tappable; revocation stays in system settings).
- The "Pick video" button **never requests any permission** — it always launches the picker directly and is always enabled (a denial degrades output quality; it never blocks the feature).
- **BREAKING** (behavioral): the "request permission before launching the picker" choreography and the one-time post-denial warning are removed, replaced by the persistent Home-screen status.
- If the system has silently auto-denied further requests (post-lockout), the Enable button routes to the app's system settings page instead of firing a request whose dialog will never appear.

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `home-screen`: new requirement — the Home screen displays the media-location permission status at all times and provides an explicit control to grant it; the permission ask is owned solely by this control.
- `cut-workflow`: the `ACCESS_MEDIA_LOCATION` requirement's request-policy paragraph is replaced — the permission is requested only from the Home-screen control, never during the pick flow; picking and cutting are never blocked by permission state; the persistent status line replaces the one-time denial warning.

## Impact

- `app/src/main/java/dev/tagalong/app/HomeScreen.kt` — the only code file changed: new status block, permission request moved to the Enable button, permission branch removed from the pick path, old inline denial warning deleted; permission state re-checked on lifecycle resume (uses `lifecycle-runtime-compose`, already a dependency).
- No manifest, `CutViewModel`, engine, or dependency changes.
- `E2eCutTest` grants `ACCESS_MEDIA_LOCATION` via `GrantPermissionRule` and clicks "Pick video" by text; both remain valid since the button stays enabled and the pick path is unchanged apart from dropping the permission branch.
