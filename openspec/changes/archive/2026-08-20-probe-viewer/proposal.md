## Why

During development, verifying that a cut preserved every metadata tag requires dropping to a terminal and running `ffprobe` manually. The app already calls `MetadataReader.probe()` (which wraps `FFprobeKit`) on the source file before every cut — that data is just discarded after `creation_time` is extracted. Surfacing it in-app lets you confirm the full tag inventory (GPS, device tags, rotation, codec) without leaving the device.

## What Changes

- `CutUiState` gains two new optional fields: `sourceProbe: MediaProbe?` and `outputProbe: MediaProbe?`
- `CutViewModel.onVideoPicked()` runs `MetadataReader.probe()` on the materialized cache file and stores the result in state (the file already exists by this point)
- `CutViewModel.runCut()` retains the existing probe call and additionally stores the output probe result after `losslessCut()` completes (before `DateTakenStore.registerAndReadBack()`)
- `CutScreen` renders a `ProbeCard` composable below the existing controls whenever `sourceProbe` is present; a second `ProbeCard` appears below the first when `outputProbe` is present (after a cut)
- Each `ProbeCard` shows a curated summary (creation_time, location tags, rotation, codec + dimensions, all `com.*` tags from all three tag maps) plus an expandable section listing every remaining raw tag

## Capabilities

### New Capabilities

- `probe-viewer`: In-app display of `MediaProbe` data for source and cut-output files, shown as two sequential cards (source card appears at pick time; output card appears after cut)

### Modified Capabilities

*(none — the cut workflow's externally observable behaviour is unchanged; probe data is already read, now it's also displayed)*

## Impact

- `:app` module: `CutUiState.kt`, `CutViewModel.kt`, `CutScreen.kt` (new `ProbeCard` composable)
- `:engine` module: `MetadataReader` and `MediaProbe` — no changes required; existing API is sufficient
- No new dependencies; `FFprobeKit` is already bundled via `ffmpeg-kit-full-gpl`
- The screen's `Column` will need `verticalScroll` to accommodate two metadata cards without overflow
