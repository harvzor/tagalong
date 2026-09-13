## Context

Two independent mechanisms decide what "dark" means for an Android app, and they do not read from
each other:

| | Controls | How it is decided today |
|---|---|---|
| **Compose colour scheme** | Everything Compose draws — all four screens | `MaterialTheme { }` with no argument ⇒ always Material 3 light |
| **Platform night mode** (`Configuration.uiMode` + the activity's theme) | Status bar icon contrast (which `enableEdgeToEdge()` reads from `uiMode`, not from the colour scheme), the window background painted before Compose's first frame, the Recents snapshot, any non-Compose `View` | Hardcoded light: `android:theme="@android:style/Theme.Material.Light.NoActionBar"` |

Because the app pins light, the two can never disagree — which is why the current single lever
looks self-consistent. Note also that `uiMode` reports the *device's* night mode regardless of the
theme name, so `isSystemInDarkTheme()` already returns the truth today; nothing consumes it.

Favourable starting condition: `grep` finds no literal colour anywhere in `:app` — every surface,
text, primary and error colour comes from `MaterialTheme.colorScheme`. The whole UI therefore
follows a scheme swap with no composable edits. The module ships exactly one resource file
(`values/strings.xml`), so there is no existing theme to reconcile.

Motivation and scope: see `proposal.md`. Behaviour contract: `specs/app-appearance/spec.md`.

## Goals / Non-Goals

**Goals:**
- Both levers read the same source — the device night mode — so they cannot drift.
- One decision point for appearance in the code, not one per screen.
- Additive change only: no dependency, no persistence, no base-class change, no composable edit.

**Non-Goals:**
- Making the app able to *disagree* with the device. An in-app override would require driving
  `uiMode` itself (via AppCompat's night mode, which only propagates to `AppCompatActivity`
  subclasses, or a hand-rolled configuration override) — that is the follow-up change's problem,
  and this change deliberately does not build toward it.
- Dynamic (Material You) colour. Adopting it would replace the baseline palette the app never
  chose, which is its own decision.
- Restyling media3's `PlayerView` chrome.

## Decisions

**D1 — Follow the system at the single `MaterialTheme` call site.**
`MainActivity` passes an explicit scheme chosen from the system night mode, in preference to
threading appearance through the screens or introducing an appearance holder. There is exactly one
`MaterialTheme` call, above the `NavHost`, so one argument covers every screen — including any
future one. `CutViewModel` stays a cut pipeline object and holds no appearance state.

**D2 — Two `Theme.Tagalong` definitions rather than a framework `DayNight` parent.**
`values/themes.xml` parents `Theme.Material.Light.NoActionBar`; `values-night/themes.xml`
overrides the same style name with `Theme.Material.NoActionBar`. Alternative considered: inherit a
framework `...DayNight` style in a single file — rejected because it depends on which API level a
given `DayNight` parent arrived at, and this way both palettes are readable side by side at
minSdk 31 with no such assumption. Alternative rejected outright: `UiModeManager`'s application
night mode — it needs a signature-level permission.

**D3 — Match the pre-Compose window background to the scheme's own background value.**
The window background comes from the platform theme and is painted before Compose renders, so a
`Theme.Material` default (#FAFAFA light / #303030 dark) is close to, but not the same as, the
Material 3 scheme background the first Compose frame paints. `values/colors.xml` + `values-night`
carry the exact scheme values, read from the scheme rather than typed from memory, so cold start
is one continuous colour.

**D4 — No status bar handling.**
`enableEdgeToEdge()` already uses an appearance style that resolves from `uiMode`. Once the
activity's theme is night-aware, both levers agree and its existing call is correct unmodified.
This is the specific claim the device check exists to confirm.

**D5 — Leave `PlayerView` alone.**
The Trim and Result previews are the only non-Compose views. media3's controller chrome is light
icons on a translucent dark scrim because it is designed to sit over arbitrary video footage, so it
should need no appearance handling. Verified visually rather than assumed, but not restyled.

## Risks / Trade-offs

- [`enableEdgeToEdge()`'s auto appearance may not track a change to the device night mode without
  an explicit re-apply] → Manual check of the status area after toggling dark mode both while foregrounded and
  while backgrounded; if it lags, re-apply the edge-to-edge style on configuration change.
- [The window background hex drifts from the scheme value after a Material 3 bump] → Keep the two
  values adjacent in `colors.xml` with a comment naming the scheme property each mirrors, so the
  drift is visible at the site of the change.
- [`PlayerView` chrome reads oddly in the dark appearance] → Low expected impact; caught by the
  visual pass, and restyling it is additive if needed.
- [Changing the device night mode recreates the activity mid-trim] → The `AndroidViewModel` survives recreation;
  the preview player is released through composition disposal and rebuilt. Covered by the
  spec's "returning after a mid-trim appearance change" scenario, which is the check that this is
  actually true rather than merely plausible.
- [The automated suites might never exercise the dark path] → They do, on the current AVD: `Medium_Phone`
  was already in night mode, so the `:app` instrumented run and the `:engine` unit run both executed
  with the device in night mode (the emulator's state is not pinned, so this is not a guarantee for
  future runs). End-to-end dark coverage would still require forcing `uiMode` from a test, which
  needs a privileged permission. A cheap guard remains available: render a screen under an
  explicitly dark scheme and assert its background is dark, which fails the moment anyone introduces
  a literal colour.
- [Trade-off accepted: users on a dark device who want the light app get neither] → They get the
  system's answer, which is correct for everyone else; the override is the deferred change.

## Open Questions

- Whether the eventual appearance toggle adopts AppCompat or a hand-rolled `uiMode` override. This
  change is neutral between the two, and nothing here commits to either.
