## 1. Enable Built-in Transport Controls

- [x] 1.1 In `VideoPreview.kt`, remove the `useController = false` line from the `PlayerView` factory lambda so ExoPlayer's built-in play/pause, seekbar, and time display are shown (design D1)

## 2. Stop Playback at Trim End Point

- [x] 2.1 In `TrimScreen.kt`, inside the `source != null` branch after `rememberVideoPlayer`, add a `LaunchedEffect(endMs)` coroutine that loops on a 100 ms delay, checks `player.isPlaying && player.currentPosition >= endMs`, and calls `player.pause()` when the condition is true (design D2)
- [x] 2.2 Confirm the `LaunchedEffect` key is `endMs` only, so the coroutine restarts with the updated boundary whenever the trim end handle is moved (design D2)

## 3. Auto-Pause on Handle Interaction

- [x] 3.1 In `TrimRangeSlider.kt`, add `if (player.isPlaying) player.pause()` as the first line of the `nudge()` function (design D3)
- [x] 3.2 In `TrimRangeSlider.kt`, add the same pause guard at the start of the `onValueChange` lambda of `RangeSlider`, before the seek-target and `onRangeChanged` calls (design D3)

## 4. Manual Verification

- [ ] 4.1 Play a clip: tap the video to reveal controls → press play → audio plays → playback stops automatically within ~100 ms of reaching `endMs`
- [ ] 4.2 Handle interaction while playing: press play → drag a trim handle → player pauses and the seeked frame is shown
- [ ] 4.3 Seek past trim end then play: use the built-in seekbar to seek beyond `endMs` → press play → playback still stops at `endMs`
- [ ] 4.4 Trim end change during playback: press play → move the End handle forward while playing → stop boundary shifts to the new `endMs`, not the old one

<!-- Manual verification steps (4.1–4.4) remain for on-device testing. -->
