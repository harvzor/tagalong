## 1. Time parser

- [x] 1.1 Add `parseMmSsTenths(input: String): Long?` to `TimeFormat.kt` — regex `^\d+:[0-5]\d\.\d$`, returns milliseconds or `null` on no match (design D3)
- [x] 1.2 Verify the parser rejects edge cases: missing tenths (e.g. `1:13`), out-of-range seconds (`1:60.0`), empty string, extra digits in tenths

## 2. Layout restructure

- [x] 2.1 Add `sealed interface EditTarget { object Start : EditTarget; object End : EditTarget }` inside `TrimRangeSlider.kt` (design D2)
- [x] 2.2 Replace the existing three-label `Row` with two per-handle rows (Start row, End row) and a centred Length label below them (design D1)

## 3. Nudge controls

- [x] 3.1 Add four `TextButton`s (`−1s`, `−0.1s`, `+0.1s`, `+1s`) to the Start row; apply clamping: `(startMs + delta).coerceIn(0L, endMs - 100L)` (design D4)
- [x] 3.2 Add four `TextButton`s to the End row; apply clamping: `(endMs + delta).coerceIn(startMs + 100L, durationMs)` (design D4)
- [x] 3.3 In each nudge click handler, call `player.seekTo(newMs)` directly — no debounce — then call `onRangeChanged(newStart, newEnd)` (design D5)

## 4. Tap-to-edit

- [x] 4.1 Add local state: `var editTarget by remember { mutableStateOf<EditTarget?>(null) }`, `var editText by remember { mutableStateOf("") }`, `var committed by remember { mutableStateOf(false) }` (design D2, D6)
- [x] 4.2 In each handle row, replace the time `Text` with a `TextField` (pre-filled, `isError` driven by parse result) when `editTarget` matches that handle; show plain `Text` otherwise — tapping the text sets `editTarget` and initialises `editText` to `formatMmSsTenths(handleMs)` (design D4, D6)
- [x] 4.3 Implement commit: `onDone` handler sets `committed = true`, parses `editText`, validates range bounds, calls `onRangeChanged` + `player.seekTo`, then resets `editTarget = null` (design D4, D6)
- [x] 4.4 Implement revert: `onFocusChanged` fires when the field loses focus; if `committed` is `false`, reset `editTarget = null` without calling `onRangeChanged`; always reset `committed = false` after handling (design D6)
- [x] 4.5 Add a `FocusRequester`; call `focusRequester.requestFocus()` inside a `LaunchedEffect(editTarget)` so the keyboard raises automatically when edit mode opens (design D6)

## 5. On-device verification

- [ ] 5.1 Nudge: confirm both step sizes move the handle and seek the player; confirm clamping at clip start, clip end, and against the opposite handle
- [ ] 5.2 Tap-to-edit: type a valid `M:SS.t` time, hit Done, confirm handle and player both move to the typed position
- [ ] 5.3 Tap-to-edit: type an invalid format and a valid-format-but-out-of-range value; confirm the error indicator appears and the handle does not move
- [ ] 5.4 Keyboard layout: confirm the trim controls remain reachable (scrollable into view) when the keyboard is open on the test AVD
