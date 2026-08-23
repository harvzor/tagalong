## Why

The `RangeSlider` thumb resolution degrades linearly with video length — on a 10-minute clip, one pixel represents ~100 ms, making sub-second precision nearly impossible by drag alone. Users need a fast escape hatch for fine-tuning without resorting to repeated drag attempts.

## What Changes

- **Layout change:** the three-label row below the slider (Start / Length / End) is replaced by two per-handle rows (Start row, End row) with a derived Length label beneath.
- **Nudge buttons:** each handle row gains four buttons — `[−1s] [−0.1s]  value  [+0.1s] [+1s]` — that shift the handle by the labelled amount. Nudge seeks the player immediately with no debounce.
- **Tap-to-edit:** tapping the time value in either row opens an inline `TextField` pre-filled with the current time in `M:SS.t` format (e.g. `1:13.3`). Only this rigid format is accepted. A red outline is shown while the input does not match the format or is out of range. Committing with the Done IME action applies the value and seeks the player; dismissing (back / outside tap) reverts to the prior value.
- **Boundary enforcement:** nudge clamps silently at `0` and at the video's full duration. Typed values that would place Start ≥ End (or End ≤ Start) are rejected with a red outline; the other handle is never moved automatically.

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `cut-workflow`: the "User can preview the source and choose a trim range" requirement gains precision-control sub-requirements — nudge steps and direct time entry — that are now externally observable behaviour with defined edge cases.

## Impact

- `app/src/main/java/dev/tagalong/app/TrimRangeSlider.kt` — primary change; layout, nudge, edit mode
- `app/src/main/java/dev/tagalong/app/TimeFormat.kt` — add `parseMmSsTenths(input: String): Long?` parser used by the edit field
