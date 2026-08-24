## Why

The video preview in TrimScreen is scrub-only — users can see frames at dragged positions but cannot play the clip with audio. Without real playback, users have no reliable way to find their cut points by watching and listening; they can only guess from still frames.

## What Changes

- Enable ExoPlayer's built-in `PlayerView` transport controller (`useController = true`), giving users a play/pause button, a full-duration seekbar for free scrubbing, time display, and audio output — all without adding a separate UI layer.
- Add a position-monitoring coroutine that auto-pauses playback when the playhead reaches `endMs` (the trim end point). This applies consistently: whether the user started playing from within the trimmed region or seeked past `endMs` manually and pressed play, playback always stops at `endMs`.
- When `endMs` changes while the video is playing (user adjusts the trim end handle), the stop boundary updates immediately on the next position check.
- Auto-pause the player when the user begins dragging a trim handle (nudge or slider drag), so the seekTo call lands cleanly on the target frame without racing with ongoing playback.

## Capabilities

### New Capabilities

_(none — this change extends existing trim-screen preview behaviour, not a new standalone capability)_

### Modified Capabilities

- `cut-workflow`: The "adjusting a trim handle updates the preview" scenario currently specifies that the preview shows the frame at the dragged position. This change additionally requires that the preview can play back the video in real-time with audio, and that playback auto-stops at the trim end point.

## Impact

- `app/src/main/java/dev/tagalong/app/VideoPreview.kt` — enable `useController = true`; remove the explicit `useController = false` line
- `app/src/main/java/dev/tagalong/app/TrimScreen.kt` — add a `LaunchedEffect` that monitors player position and pauses at `endMs`; wire `endMs` as an effect key so the boundary refreshes when the trim end changes
- `app/src/main/java/dev/tagalong/app/TrimRangeSlider.kt` — call `player.pause()` at the start of `nudge()` and inside `onValueChange` if the player is currently playing
- No new Gradle dependencies — `media3-exoplayer` and `media3-ui` are already declared; `PlayerView`'s built-in controller is part of `media3-ui`
