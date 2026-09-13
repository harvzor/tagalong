## Why

The launcher label "Tagalong" says nothing about what the app does. A user scanning
the app drawer — or searching it — has no keyword to find the app by. Adding the
descriptive suffix "Video Cutter" makes the app self-explanatory at the moment of
discovery, at zero functional cost.

## What Changes

- The `app_name` string resource (`app/src/main/res/values/strings.xml`) changes
  from `Tagalong` to `Tagalong: Video Cutter`. This is the single source of the
  label consumed by `android:label` on `<application>` in `AndroidManifest.xml`.
- Every system surface that renders the application label updates accordingly:
  app drawer, home screen icon caption, recents/task switcher, and the
  system Settings → Apps entry.
- In-app branding is **unchanged**: `HomeScreen.kt` and `AboutScreen.kt` continue
  to render the wordmark "Tagalong". A store-style launcher label paired with a
  short in-app brand is the intended end state, not an oversight.
- No activity-level `android:label` is introduced; the one-label-one-source
  model is kept.

## Capabilities

### New Capabilities

- `launcher-identity`: How the app identifies itself on system surfaces outside
  the app's own drawn content — the launcher label text and its relationship to
  the in-app brand.

### Modified Capabilities

_(none — no existing spec's requirements change; `app-appearance` covers
light/dark appearance only, and `home-screen`/`about-screen` specs do not
constrain the launcher label.)_

## Impact

- **Code**: one line in `app/src/main/res/values/strings.xml`. No manifest,
  Kotlin, Gradle, or resource-identifier changes.
- **Unaffected identifiers**: `Theme.Tagalong` / `TagalongTheme` (style names),
  `namespace`/`applicationId` `dev.tagalong.app`, and the `Movies/Tagalong/`
  output directory — all are identifiers or paths, not the label.
- **Tests**: no instrumented or unit test asserts on the launcher label (verified
  by grep across `:app` and `:engine` test sources), so no test churn.
- **Launchers**: "Tagalong: Video Cutter" is 22 characters; dense launchers may
  wrap it to two lines or ellipsize it. Accepted trade-off for keyword
  discoverability (see design.md).
- **Play Store**: the store listing name is a separate Play Console field and is
  not touched by this change.
