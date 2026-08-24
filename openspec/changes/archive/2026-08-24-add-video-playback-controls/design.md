## Context

ExoPlayer (`media3-exoplayer`) and `PlayerView` (`media3-ui`) are already in the dependency graph and wired into `VideoPreview.kt`. The player is currently created via `rememberVideoPlayer(file)` and passed to both `VideoPreview` and `TrimRangeSlider`. `PlayerView` is rendered with `useController = false` — the built-in transport overlay is present in the library but suppressed. `TrimRangeSlider` calls `player.seekTo()` on handle changes and nudges. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Enable real-time video playback with audio in the existing preview surface
- Auto-stop playback at the trim end point (`endMs`) on every code path
- Pause playback when the user begins interacting with a trim handle
- Minimise new code — leverage ExoPlayer's built-in transport UI

**Non-Goals:**
- Custom playback controls or a persistent controls bar
- Loop playback (single play-through, then stop)
- Restricting the seek range to the trimmed region (user can seek freely across the full clip)
- Re-encoding or preview of the re-encode path

## Decisions

### D1 — Use ExoPlayer's built-in `PlayerView` controller (`useController = true`)

Removing `useController = false` (one line) gives play/pause, a full-duration seekbar, and time display for free. The alternative — a custom persistent controls row (a `Slider` + `IconButton` below the video) — would be always visible but requires wiring up position polling to keep the slider position live, handling drag gestures, and syncing player state reactively. The built-in controller's auto-hide behaviour (appears on tap, hides after ~3 s) is standard video UX and acceptable for a trim tool.

### D2 — Enforce the stop boundary with a polling coroutine, not `ClippingConfiguration`

ExoPlayer's `MediaItem.ClippingConfiguration` confines both playback and the seekbar to a sub-range. That satisfies the stop-at-endMs requirement but prevents the user from seeking outside the trimmed region — which the spec requires to remain possible. A `LaunchedEffect` coroutine that polls `player.currentPosition` every 100 ms and calls `player.pause()` when `position >= endMs` keeps the seekbar full-width while still honouring the boundary. The coroutine uses `endMs` as a `LaunchedEffect` key so a trim handle change restarts it with the updated boundary immediately.

**Polling interval — 100 ms:** Coarse enough to have no perceptible CPU impact; fine enough that overshoot (the video plays at most ~100 ms past the end point before pausing) is imperceptible for trim use. If sub-frame accuracy is ever needed, the polling coroutine can be replaced with a `Player.Listener.onEvents()` callback without changing any spec or task breakdown.

### D3 — Auto-pause at start of `nudge()` and `onValueChange` in `TrimRangeSlider`

`player.isPlaying` is a synchronous property; calling `player.pause()` before `player.seekTo()` is safe and makes the seeked frame deterministic (no race between the player's advancing position and the caller's seek target). The user resumes play manually. This matches the modified spec scenario: "if playback was active it SHALL be paused before the seek."

## Risks / Trade-offs

**Overshoot up to one poll interval (~100 ms)** — The polling approach means playback can run slightly past `endMs` before the coroutine fires the pause. For a video trim tool this is not a precision concern (the cut itself is performed by ffmpeg from the exact `endMs` value). If this becomes noticeable, reduce the interval to 50 ms or switch to `Player.Listener`. → Acceptable as-is for v1.

**Built-in controller auto-hide** — The transport controls disappear after ~3 s and require a tap on the video surface to restore. First-time users may not discover this immediately. → No mitigation planned; this is a standard Android video player pattern.

**`rememberVideoPlayer` uses `Uri.fromFile(file)`** — The player is already constructed from a local cached file, so audio permissions and content URI redaction are not concerns here. No change needed to player construction.

## Migration Plan

No data migration or rollout steps required. The change is purely additive UI behaviour; existing cuts and saved files are unaffected. The one removed line (`useController = false`) cannot be rolled back accidentally — reverting the commit restores it.
