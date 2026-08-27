## Context

See proposal.md — Why.

The app currently has two Compose nav destinations (`trim`, `result`). `TrimScreen` owns both the empty state (picker launch) and the loaded state (trim controls). `CutUiState.source` is nullable, reflecting this dual role.

## Goals / Non-Goals

**Goals:**
- Four nav destinations: `home`, `trim`, `result`, `about`
- TrimScreen always renders a loaded video — no empty state
- OpenDocument launcher and ACCESS_MEDIA_LOCATION permission request live in HomeScreen
- AboutScreen contains the ffmpeg-kit attribution required by LGPL v3 §4(a)

**Non-Goals:**
- Recent files list or any persistent state on HomeScreen
- An automated open-source-licenses screen (e.g. mikepenz/aboutlibraries) — manual attribution is sufficient
- Changes to trim or cut behaviour

## Decisions

### Nav graph: flat, four destinations

```
home ──[pick]──▶ trim ──[cut]──▶ result
  ▲                │                │
  │    [back]──────┘    [back/done]─┘
  │
  └──[about]──▶ about
                  │
       [back]─────┘
```

`home` is the start destination. `about` is a dead-end — back always pops to `home`. `result` back also pops to `home` (not `trim`), so users aren't dropped back into a trim session after saving.

**Alternative considered:** keeping `trim` as start destination and adding a back-stack entry for `home`. Rejected — it couples two different concerns in one screen and makes the empty/loaded state harder to reason about.

### Picker ownership moves to HomeScreen

`OpenDocument` launcher and the `ACCESS_MEDIA_LOCATION` permission request currently live in `TrimScreen`. They move to `HomeScreen`. On successful pick, `HomeScreen` calls `viewModel.onVideoPicked(uri)` then navigates to `trim`.

This lets `TrimScreen` drop the null-source guard and the empty-state composable entirely.

### CutUiState.source remains nullable in the ViewModel

`CutUiState.source` stays `PickedSource?` in `CutViewModel` — the ViewModel has no opinion about which screen owns the picker. `TrimScreen` can assert non-null via `requireNotNull` or simply trust the nav graph (it is never navigated to without a prior `onVideoPicked` call).

**Alternative:** make source non-nullable, add a separate sealed event for "no source" state. Rejected — over-engineering for a single-source app; the nav graph is the guard.

### AboutScreen: static Compose, no library

The attribution content is fixed at compile time. A static Compose screen with a `Text` + `ClickableText` (or `TextButton`) for the source link is sufficient. No `aboutlibraries` plugin needed.

## Risks / Trade-offs

- [Risk] Deep-linking or process-death restore could navigate to `trim` with a null source → Mitigation: `TrimScreen` guards with `LaunchedEffect` that pops to `home` if source is null, matching existing behaviour
- [Risk] Version number on About screen goes stale → Mitigation: read from `BuildConfig.VERSION_NAME` at runtime, not a hardcoded string
