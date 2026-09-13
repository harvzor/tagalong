## Why

The app is pinned to a light theme at the platform level: `AndroidManifest.xml` declares
`@android:style/Theme.Material.Light.NoActionBar` and `MainActivity` calls `MaterialTheme { }`
with no colour scheme, which resolves to Material 3's light palette. A user whose device is in
dark mode therefore gets a glaring white app — today that is a defect, not a missing feature.

Fixing "follow the system" first is deliberate: it is the part that is nearly free, and it is
the part a user who already chose dark mode should not have to opt into. An in-app appearance
*toggle* is a separate, larger decision (it can put the app's Compose colours and the platform's
night mode out of agreement, and it needs a persistence layer the app does not have) and is
deliberately deferred.

## What Changes

- The app's platform theme becomes night-aware: a `Theme.Tagalong` style defined in both
  `values/` and `values-night/`, referenced from the manifest in place of the hardcoded
  `Theme.Material.Light.NoActionBar`.
- The Compose colour scheme is selected from the system night mode instead of always being light.
- The window background painted before Compose draws is matched to the active scheme, so a cold
  start does not flash the wrong shade.
- Every existing screen inherits the new scheme unchanged in structure — no screen uses a
  literal colour, so no composable needs colour edits.
- Out of scope, explicitly: an appearance toggle, any in-app override of the system setting,
  persisted preferences, an AppCompat dependency, and dynamic (Material You) colour.

## Capabilities

### New Capabilities

- `app-appearance`: how the app chooses light or dark presentation — following the system night
  mode, reacting to a change without a restart, and keeping the non-Compose surfaces it is
  responsible for (system bar icon contrast, pre-Compose window background) consistent with the
  scheme it renders.

### Modified Capabilities

None. No existing requirement changes: no new control appears on any screen (`home-screen`), and
the transition requirements in `screen-navigation` — including "App appearance is unchanged at
rest", which concerns screen overlap during transitions — still hold as written.

## Impact

- `app/src/main/AndroidManifest.xml` — application theme reference.
- `app/src/main/res/` — new `values/themes.xml`, `values-night/themes.xml`, `values/colors.xml`.
  The module currently ships `values/strings.xml` and nothing else.
- `app/src/main/java/dev/tagalong/app/MainActivity.kt` — the single `MaterialTheme` call site.
- No new dependencies; no `:engine` changes; no change to cut behaviour or metadata handling.
- Verification: existing instrumented suites make no colour assertions, so they are unaffected
  either way. Manual dark-mode pass on the emulator, listed in tasks.
