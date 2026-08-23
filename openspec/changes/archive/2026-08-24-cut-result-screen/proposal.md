## Why

After a cut the user has no focused place to verify that their metadata survived — the output probe card is buried below the trim controls in the same scrolling view, requiring mental effort to compare two separate cards. A dedicated result screen, navigated to after a successful cut, gives the user a clear "before vs after" moment and makes the app's core value proposition (metadata preservation) immediately visible.

## What Changes

- **Add a Result screen (Window 2)** that appears after a successful cut, showing the cut video preview, the full absolute output path, and a unified metadata diff table comparing source and output tags side-by-side.
- **Promote the Trim screen (Window 1)** to show the full absolute path of the source video (not just the gallery-relative segment).
- **Replace the two-card probe display** with the unified diff view on the Result screen. The source probe card on the Trim screen remains as a small reference (unchanged from today, below the fold).
- **Add Navigation Compose** as the navigation foundation. Two destinations: `trim` and `result`. The Result screen has a Back button that returns to the Trim screen with trim state intact so the user can re-cut.
- The metadata diff table shows every curated tag row with a Source column and a Cut output column. A summary banner at the top of the card reads "✓ All N tags preserved" (green) or "⚠ N tags missing/changed" (amber). Rows that differ or are absent in the output are highlighted.
- The expandable overflow section ("N more tags") is preserved in the diff view, with per-row status carried through.

## Capabilities

### New Capabilities

- `cut-result-screen`: Dedicated post-cut screen — cut video preview, full output path, back navigation to trim screen, unified metadata diff card with summary banner.

### Modified Capabilities

- `cut-workflow`: Trim screen now shows the full absolute path of the source video. After a successful cut the app navigates forward to the result screen (not inline state change). Trim state (start/end handles) is preserved when navigating back from result to trim.
- `probe-viewer`: Metadata display model changes — the two separate probe cards are replaced by a single unified diff card on the result screen. The diff card shows source and output values in parallel columns with per-row status indicators and a top-level summary banner.

## Impact

- `app/` — `CutScreen.kt` split into `TrimScreen.kt` + `ResultScreen.kt`, wrapped in a `NavHost` in `MainActivity`. `CutUiState` extended to carry the output file's absolute path. Navigation Compose added as a dependency (`androidx.navigation:navigation-compose`).
- `ProbeCard.kt` — replaced or extended with a `MetadataDiffCard` composable that accepts both source and output `MediaProbe` and renders the diff layout.
- No changes to `:engine` or `:cutdebug`.
