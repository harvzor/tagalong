## Context

`TrimRangeSlider` is a self-contained Composable in `app/`. It receives `startMs`/`endMs` from the ViewModel, exposes changes via `onRangeChanged`, and drives the ExoPlayer for preview seeks. The current bottom row shows three `Text` labels (Start / Length / End). `TimeFormat.kt` has `formatMmSsTenths` but no parser.

See `proposal.md – Why` for motivation.

## Goals / Non-Goals

**Goals:**
- Add nudge buttons (−1 s, −0.1 s, +0.1 s, +1 s) per handle inside `TrimRangeSlider`
- Add tap-to-edit text fields (rigid `M:SS.t` format) inside `TrimRangeSlider`
- Keep all precision-control logic inside the Composable; the ViewModel API (`onRangeChanged`) is unchanged

**Non-Goals:**
- Keyframe-snap indication (deferred to step 3 polish)
- Variable nudge step size or user-configurable steps
- Changing the ViewModel, engine, or any module outside `:app`

## Decisions

### D1 — Compact main row + per-handle dialog (revised)

Keep the existing three-label row (Start | Length | End) but make Start and End tappable (primary colour, indicating interactivity). Tapping either opens a focused `AlertDialog` containing the nudge buttons and an editable `M:SS.t` text field.

```
Start    00:12.3        Length   01:45.2        End   01:57.5
            ↑ tap                                  ↑ tap
```

Dialog (for Start or End):
```
┌────────────────────────────────┐
│ Start                          │
├────────────────────────────────┤
│  ┌──────────────────────────┐  │
│  │  00:12.3                 │  │
│  └──────────────────────────┘  │
│  [−1s]  [−0.1s]  [+0.1s]  [+1s] │
├────────────────────────────────┤
│  [Cancel]                [OK]  │
└────────────────────────────────┘
```

**Rationale:** four nudge buttons per handle don't fit alongside label and time in a single row on a phone-width screen (~360 dp). Moving controls into a dialog gives them full width and preserves the clean main layout. Nudge buttons in the dialog seek the player immediately for live preview; changes are committed only on OK.

**Alternative considered:** inline per-handle rows (original D1) — rejected; on-device evaluation showed the result was too cramped and visually noisy.

### D2 — Edit state lives in `TrimRangeSlider` local state

```kotlin
var editTarget by remember { mutableStateOf<EditTarget?>(null) }
// EditTarget.Start | EditTarget.End

var editText by remember { mutableStateOf("") }
```

When `editTarget` is non-null, the corresponding time label is replaced with a `TextField`. The other handle's nudge buttons remain active.

**Rationale:** edit mode is transient UI state with no effect until committed — it doesn't belong in the ViewModel or `CutUiState`. Keeping it local avoids leaking uncommitted text into app state.

### D3 — `parseMmSsTenths` added to `TimeFormat.kt`

```kotlin
/** Parses `M:SS.t` (e.g. "1:13.3") → milliseconds, or null if invalid. */
fun parseMmSsTenths(input: String): Long?
```

Regex: `^\d+:[0-5]\d\.\d$`. Rejection is strict — partial matches (no tenths, extra digits) return null, which keeps the field in error state.

### D4 — Validation and clamping in `TrimRangeSlider`

- **Nudge:** `(startMs + delta).coerceIn(0, endMs - 1)` for Start; `(endMs + delta).coerceIn(startMs + 1, durationMs)` for End. No-op if clamping would produce no movement (already at boundary).
- **Typed value:** after parsing, check `parsed in 0 until endMs` for Start and `parsed in (startMs + 1)..durationMs` for End. Out-of-range → keep error indicator, do not call `onRangeChanged`.

Error state exposed to `TextField` via `isError = true` (Material3 parameter), which renders the red outline.

### D5 — Nudge seeks without debounce

Nudge is a discrete tap, not a continuous drag. Call `player.seekTo(newMs)` directly in the click handler, bypassing the existing 50 ms `LaunchedEffect` debounce used by the slider drag. The existing debounce path is unchanged.

### D6 — IME action and keyboard dismissal

`TextField` uses `KeyboardOptions(imeAction = ImeAction.Done)` and `KeyboardActions(onDone = { commit() })`. A `FocusRequester` requests focus when `editTarget` becomes non-null, which raises the keyboard. Tapping outside or navigating away triggers `onFocusChanged`; if the field loses focus without a commit, revert by resetting `editText` to the formatted current value and setting `editTarget = null`.

## Risks / Trade-offs

- **Focus loss detection reliability:** `onFocusChanged` fires on focus loss but the ordering with IME dismissal can be tricky on some API levels. Risk: revert fires before commit on Done. Mitigation: use a committed flag — set it to `true` in `onDone` before clearing focus, and skip the revert in `onFocusChanged` if the flag is set.
- **Keyboard pushing content off-screen:** the screen already uses `verticalScroll`; the system's `WindowInsets` handling should scroll the controls into view, but this should be checked on-device. The video preview is capped at 320 dp, so there is room.
- **Row width at small screen sizes:** four nudge `TextButton`s plus a time label in one row may wrap on very narrow screens (<320 dp). Mitigation: use `TextButton` with compact padding; labels are short (`−1s`, `+1s`). If wrapping occurs, `IconButton` with tooltip could replace text labels.

## Open Questions

_(none — all decisions needed for the task breakdown are resolved above)_
