## Context

See proposal.md — Why for root-cause detail.

The relevant code path is `CutViewModel.materializeToCache(uri)`, which calls `ContentResolver.openInputStream(uri)` to byte-copy the picked video to a cache file. Android's media framework intercepts this stream and strips GPS tags when the calling app does not hold `ACCESS_MEDIA_LOCATION`. The fix is to hold the permission before opening the stream.

`ACCESS_MEDIA_LOCATION` is a dangerous permission (API 29+, always within minSdk 31). It must be declared in the manifest **and** requested at runtime. The app currently declares zero permissions; this is the first runtime permission request anywhere in the app.

## Goals / Non-Goals

**Goals:**
- Declare `ACCESS_MEDIA_LOCATION` in the manifest
- Request the permission at runtime before the file picker launches
- Show a non-blocking warning when denied, then still open the picker
- Preserve the "no manual tag injection" principle — GPS flows through `openInputStream` bytes, not through any re-stamping code

**Non-Goals:**
- Re-stamping GPS metadata after the fact (the byte-stream fix makes this unnecessary)
- Requesting any other media permission (the app intentionally holds no broad media access)
- Caching the permission result across app restarts (OS already handles this)

## Decisions

### D1 — Request permission in CutScreen, not CutViewModel

The permission request is a UI concern (`ActivityResultContracts.RequestPermission` produces a Compose-compatible launcher). `CutViewModel` stays pure logic: no `Context` usages added, no permission check in business logic.

*Alternative:* check permission status in `onVideoPicked` and return an error — rejected because it inverts the flow (pick first, discover missing permission second) and the spec requires the request to happen *before* the picker.

### D2 — Ask on every "Pick video" tap when not yet granted, not on app launch

Requesting permission at launch is unexplained and typically rejected at a higher rate. Tying the request to the explicit user action ("Pick video") gives the rationale a natural moment: the user just expressed intent to work with a video, so a prompt about location in that video is contextually clear.

*Alternative:* ask once on first launch, store result — rejected because the OS permission model already remembers the answer; duplicating that state creates divergence bugs.

### D3 — Warning placement: show inline below the pick button, not a Toast

A one-time `Text` composable shown in place of (or below) the pick button, visible only when the permission is denied, survives rotation and process death cleanly because it derives from the observable permission state rather than a transient side-effect call. A Toast is fire-and-forget and can be missed.

*Alternative:* `Snackbar` — reasonable but adds scaffolding not otherwise present in the screen; a simple `Text` row is consistent with how `CutState.Error` is already surfaced in `CutStateStatus`.

### D4 — Permission state is checked live, not stored in `CutUiState`

`ContextCompat.checkSelfPermission` is cheap and always reflects the current OS state. Adding a `locationPermissionDenied: Boolean` flag to `CutUiState` would go stale if the user grants from Settings mid-session and returns to the app. Live check avoids that.

## Risks / Trade-offs

**[Risk] Play Store review friction** — adding a permission named `ACCESS_MEDIA_LOCATION` may prompt Play Store reviewers to question why a video trimmer needs location access.
→ Mitigation: the existing CLAUDE.md justification section already explains the `ACTION_OPEN_DOCUMENT` choice in detail; extend it with a parallel paragraph explaining `ACCESS_MEDIA_LOCATION` is needed to read — not collect — location that is already embedded in files the user explicitly selects.

**[Risk] Emulator tests do not cover the permission-denied path** — the E2eCutTest runs on the emulator, which auto-grants permissions for test APKs. The denied-permission warning path will not be exercised by existing instrumented tests.
→ Mitigation: this is a display-only code path (a `Text` composable gated on permission status). Manual verification on a physical device suffices for the initial implementation; a dedicated UI test can be added later.

**[Risk] `ACCESS_MEDIA_LOCATION` silently has no effect on some OEM builds** — a minority of heavily customised Android forks (some Xiaomi HyperOS builds, some OPPO variants) have been reported to sanitise streams regardless of this permission.
→ Mitigation: out of scope for this change. The permission is the standard Android fix. If specific OEM builds still strip location even with the permission, that is an OEM bug requiring a separate investigation. The ProbeCard already surfaces the `location` field so users can see the result.

## Open Questions

_(none — all decisions above are unblocked)_
