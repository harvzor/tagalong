## Context

See proposal.md — Why for motivation.

`CutScreen.kt` is a single `Column` with no explicit centering, horizontal alignment, or button hierarchy. `PickedSource` already carries `originalDisplayName`; it does not carry a gallery-relative path. The MediaStore query in `CutViewModel.onVideoPicked` currently requests only `DISPLAY_NAME`.

## Goals / Non-Goals

**Goals:**
- Center the empty-state call-to-action on the screen
- Make all action buttons full-width, consistent with Material 3 conventions
- Give "Cut and save" clear visual priority over "Pick a different video"
- Expand the video preview to use available screen width
- Show the source video's gallery-relative path (or filename fallback) above the preview

**Non-Goals:**
- Adding a `TopAppBar` or navigation structure (future milestone)
- Mode toggle UI (step 2, separate change)
- Theming or branding beyond what Material 3 defaults provide

## Decisions

### D1 — Single `displayPath: String` field on `PickedSource`

Store the combined display string (relative path + filename, or filename-only fallback) on `PickedSource` rather than two separate fields.

**Rationale:** `CutScreen` has no reason to know whether a relative path was available. The ViewModel assembles the display string where the MediaStore columns are resolved, keeping the UI dumb.

**Alternative considered:** Two fields (`relativePath: String?`, `originalDisplayName`). Rejected — the UI would have to handle the null/non-null join logic, which belongs in the ViewModel.

### D2 — Extend the existing MediaStore cursor to include `RELATIVE_PATH`

Add `MediaStore.MediaColumns.RELATIVE_PATH` to the column array already passed to `resolver.query(...)` in `onVideoPicked`. Build `displayPath` as `"$relativePath$displayName"` when non-null, else `displayName` alone.

**Rationale:** The query is already open at that point — adding a column is free. No second query needed.

**Known caveat:** The Google Photopicker redacts `RELATIVE_PATH` (same sandboxing that redacts GPS). The fallback to filename-only is the correct response; this is documented in CLAUDE.md under known gaps.

### D3 — `fillMaxWidth()` on all action buttons

Apply `Modifier.fillMaxWidth()` uniformly. No `width = ...` pixel values.

**Rationale:** Material 3 full-width buttons in single-column layouts are the standard Android pattern for primary actions.

### D4 — `OutlinedButton` for "Pick a different video"

Demote from `Button` (filled) to `OutlinedButton`. No other changes to its behaviour or position.

**Rationale:** Makes the primary action ("Cut and save") visually dominant without hiding the secondary option.

### D5 — Empty state: vertically-centered column

Wrap the empty-state content in a sub-`Column` with `Modifier.fillMaxSize()` and `verticalArrangement = Arrangement.Center`, `horizontalAlignment = Alignment.CenterHorizontally`.

**Rationale:** Puts the button in the natural thumb zone and removes the visual imbalance of a lone element in the top-left corner.

### D6 — Video preview: `fillMaxWidth()` with intrinsic aspect ratio

Apply `Modifier.fillMaxWidth()` to the `VideoPreview` composable (currently rendered at its natural intrinsic size). `VideoPreview` should honour this by not overriding width internally.

**Rationale:** The preview currently uses only a fraction of the available horizontal space, wasting screen real estate.

## Risks / Trade-offs

- **RELATIVE_PATH redaction by Google Photopicker** → Mitigated by filename-only fallback (D2). No user-visible failure; they simply see less path detail.
- **Very long filenames** → `displayPath` rendered with `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to avoid wrapping or pushing controls off-screen.
- **Video preview aspect ratio on tall portrait clips** → `fillMaxWidth()` can make a 9:16 clip very tall, potentially pushing buttons below the fold. Constrained by a `heightIn(max = ...)` or letting the preview use `wrapContentHeight` with `fillMaxWidth` — implementer to choose the balance that keeps controls visible.
