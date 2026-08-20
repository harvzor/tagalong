## 1. State layer

- [x] 1.1 Add `sourceProbe: MediaProbe? = null` and `outputProbe: MediaProbe? = null` to `CutUiState`
- [x] 1.2 In `CutViewModel.onVideoPicked()`, call `MetadataReader.probe(file)` on `Dispatchers.IO` after `materializeToCache()` and store the result as `sourceProbe` in state; reset `outputProbe` to null on each new pick
- [x] 1.3 In `CutViewModel.runCut()`, after `losslessCut()` and before `DateTakenStore.registerAndReadBack()`, call `MetadataReader.probe(output)` and include the result as `outputProbe` when emitting `CutState.Saved`

## 2. ProbeCard composable

- [x] 2.1 Create `ProbeCard.kt` in `:app`; add a `ProbeCard(label: String, probe: MediaProbe)` composable
- [x] 2.2 Render the curated summary rows: `creation_time` (formatted), location tags (value or `"—"`), rotation (`"<N>°"` or `"—"`), video codec + dimensions, and all `com.*` keys sorted alphabetically across all three tag maps
- [x] 2.3 Add an expand/collapse control; when expanded, list every remaining tag key-value pair (not already in the curated rows) sorted alphabetically

## 3. CutScreen integration

- [x] 3.1 Add `Modifier.verticalScroll(rememberScrollState())` to the root `Column` in `CutScreen`
- [x] 3.2 Below the existing controls, render `ProbeCard("Source", uiState.sourceProbe!!)` when `sourceProbe` is non-null
- [x] 3.3 Below the source card, render `ProbeCard("Cut output", uiState.outputProbe!!)` when `outputProbe` is non-null
