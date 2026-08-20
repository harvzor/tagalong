## Context

See `proposal.md — Why` for motivation.

`MetadataReader.probe(file: File): MediaProbe` already exists in `:engine` and already wraps `FFprobeKit.getMediaInformation()`. In `CutViewModel.runCut()`, the probe is called on the source file but only `creation_time` is kept; the rest of the `MediaProbe` is discarded. At pick time, the source has already been materialized to `cacheDir/input.<ext>` — a plain `File` — so a probe call there requires no new I/O setup. The cut output is also a `File` in `cacheDir` immediately after `losslessCut()` and before `DateTakenStore.registerAndReadBack()`, which is the last moment it has an addressable path (MediaStore inserts it as a `content://` URI after that).

## Goals / Non-Goals

**Goals:**
- Surface `MediaProbe` data in `CutUiState` for both source (at pick time) and output (at cut time)
- Render it as two sequential `ProbeCard` composables in `CutScreen`
- Show a fixed curated set immediately; expose remaining tags via expand/collapse

**Non-Goals:**
- Diffing or change-highlighting between source and output (user reads both visually)
- Editing or copying tag values
- Showing probe data for files outside the current pick/cut session

## Decisions

### D1 — Probe at pick time, not lazily

**Decision:** Call `MetadataReader.probe()` inside `onVideoPicked()` on `Dispatchers.IO`, immediately after `materializeToCache()`.

**Rationale:** The materialized file is ready at that point and the probe is fast (byte-only, no decode). Storing a pre-populated `sourceProbe` in state means the card renders immediately — no extra tap or state toggle needed.

**Alternative considered:** Run the probe only when the user expands a "show metadata" button. Rejected: spec requires the card to appear without user action, and delaying means the card appears mid-session with a visible loading flash.

### D2 — Store `MediaProbe?` in `CutUiState`

**Decision:** Add `sourceProbe: MediaProbe?` and `outputProbe: MediaProbe?` to `CutUiState`. Both are null until set; `outputProbe` is reset to null on a new pick.

**Rationale:** Follows the existing pattern (`CutState`, `PickedSource`) of making all renderable state observable via `StateFlow<CutUiState>`. No separate state holder is needed.

**Alternative considered:** A separate `StateFlow<MediaProbe?>` per probe. Rejected: two extra flows with their own `collectAsState()` calls in the composable; the existing pattern is simpler.

### D3 — Capture output probe before `DateTakenStore.registerAndReadBack()`

**Decision:** After `losslessCut()` writes the output `File` and before it is handed to `DateTakenStore`, call `MetadataReader.probe(output)` and store the result alongside `galleryDateMillis` in a local pair, then emit both to `CutUiState`.

**Rationale:** After `DateTakenStore.registerAndReadBack()` the file is moved into the MediaStore and its `File` path may no longer be valid. The cache copy is the only window with a stable path.

**Alternative considered:** Re-probe via a `content://` URI after gallery insertion. Rejected: `FFprobeKit.getMediaInformation()` takes a file path; it cannot consume a URI. A `ParcelFileDescriptor` bridge would add non-trivial complexity for a debug feature.

### D4 — `ProbeCard` curated fields

The curated tier shows exactly:
1. `creation_time` from `formatTags` → formatted as a readable date string (`yyyy-MM-dd HH:mm:ss UTC` is fine for debugging)
2. `location` and/or `location-eng` from `formatTags` → raw value, or `"—"` if both absent
3. `videoRotationDegrees` → `"<N>°"` or `"—"` if null
4. `videoMime` + `videoWidth` × `videoHeight` → e.g. `"video/hevc · 1080×1920"`
5. All keys from `formatTags + videoStreamTags + audioStreamTags` where `key.startsWith("com.")`, deduplicated by key, sorted alphabetically

The expandable section shows every remaining key-value pair (those not already shown in the curated tier) from all three tag maps, sorted alphabetically.

**Rationale:** This set corresponds directly to the known preservation-critical fields (see `tagalong-overview.md`) and the open rotation gap. It will make regressions visible at a glance during development.

### D5 — Make `CutScreen` vertically scrollable

**Decision:** Add `Modifier.verticalScroll(rememberScrollState())` to the root `Column` in `CutScreen`.

**Rationale:** Two metadata cards added below the existing controls will overflow a typical phone screen. A scrollable column is the lowest-friction fix and consistent with Android convention for single-screen forms that grow taller than the viewport.

**Alternative considered:** A two-zone layout (fixed controls + scrollable bottom zone). Rejected: over-engineered for a debug feature; the controls remain accessible by scrolling up.

## Risks / Trade-offs

- **Probe cost at pick time:** `FFprobeKit.getMediaInformation()` runs on `Dispatchers.IO` and is already fast on the test fixture (`xiaomi-poco-x5.mp4`). For very large files the first metadata read might take 1–2 s, but this is acceptable for a debug screen and the call runs off the main thread. → No mitigation needed now; revisit if it becomes noticeable on large files.
- **`MediaProbe` is an engine type in the UI layer:** `CutUiState` will hold a `:engine` type directly. This is already the case for `CutEngine`/`FfmpegCutEngine` usage in the ViewModel; the modules are already coupled. → Acceptable for a single-module app at this stage.
- **Rotation is `Int?` not a human string:** `videoRotationDegrees` can be null (e.g., no display matrix). The UI must handle null gracefully. → Show `"—"` when null.
