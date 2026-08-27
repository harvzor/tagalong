## Why

The app currently has no dedicated home screen — the trim screen doubles as the entry point with an empty state, making navigation awkward (there's nowhere to "go back to") and leaving no natural place for an About screen. Adding a Home screen and a separate About screen gives the app a clean navigation model and satisfies the LGPL v3 §4(a) requirement to give prominent notice that ffmpeg-kit is used.

## What Changes

- **New Home screen** — first screen users see; contains the "Pick video" button and a link to the About screen. The `OpenDocument` launcher and `ACCESS_MEDIA_LOCATION` permission request move here from TrimScreen.
- **New About screen** — dedicated page showing app name, version, GPL v3 license notice, and ffmpeg-kit-full-gpl attribution (name, version, author, license, link to source).
- **TrimScreen simplified** — the empty state (no video picked) is removed; TrimScreen now always renders with a loaded video. Back navigation goes to Home.
- **ResultScreen** — back navigation goes to Home rather than Trim.
- **Nav graph** — four destinations: `home`, `trim`, `result`, `about`.

## Capabilities

### New Capabilities

- `home-screen`: The app's entry point — lets users pick a video and navigate to About.
- `about-screen`: Displays app metadata and open-source attribution for ffmpeg-kit-full-gpl.

### Modified Capabilities

_(none — TrimScreen changes are implementation detail; its core trim behaviour is unchanged)_

## Impact

- `MainActivity.kt` — nav graph gains `home` and `about` destinations
- `TrimScreen.kt` — empty state removed; `OpenDocument` launcher and permission request removed
- `CutViewModel.kt` / `CutUiState.kt` — `source` field becomes non-nullable after pick (enforced by only navigating to trim after a pick)
- New files: `HomeScreen.kt`, `AboutScreen.kt`
- No new dependencies required
