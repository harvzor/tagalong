## 1. Dependencies and data model

- [x] 1.1 Add `androidx.navigation:navigation-compose` to `:app` `build.gradle.kts` (version via Compose BOM)
- [x] 1.2 Add `absolutePath: String?` field to `PickedSource` in `CutUiState.kt`
- [x] 1.3 Introduce `SaveResult(dateTakenMillis: Long?, absolutePath: String?)` data class in `DateTakenStore.kt`; change `registerAndReadBack` return type to `SaveResult`; populate `absolutePath` by calling the existing `legacyPathFor` after the insert
- [x] 1.4 Update `CutState.Saved` in `CutUiState.kt` to add `outputAbsolutePath: String?`

## 2. ViewModel updates

- [x] 2.1 In `CutViewModel.onVideoPicked`: construct the source `absolutePath` from `Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath + displayName` when `relativePath` is non-null; store in `PickedSource.absolutePath`
- [x] 2.2 In `CutViewModel.runCut`: adapt `DateTakenStore.registerAndReadBack` call to the new `SaveResult` return; forward `absolutePath` into `CutState.Saved.outputAbsolutePath`

## 3. Navigation scaffold

- [x] 3.1 In `MainActivity.kt`: replace the direct `CutScreen()` call with a `NavHost` (start destination `"trim"`) containing two `composable` routes: `"trim"` and `"result"`
- [x] 3.2 Pass `navController` down to `TrimScreen` so it can trigger forward navigation

## 4. TrimScreen

- [x] 4.1 Rename `CutScreen.kt` → `TrimScreen.kt`; rename the top-level Composable to `TrimScreen(navController, viewModel)`
- [x] 4.2 Update the source path label to display `source.absolutePath` when non-null, falling back to `source.displayPath`, then filename-only
- [x] 4.3 Add a `LaunchedEffect(cutState)` that calls `navController.navigate("result") { launchSingleTop = true }` when `cutState` transitions to `CutState.Saved`
- [x] 4.4 Remove the `uiState.outputProbe?.let { ProbeCard("Cut output", it) }` line — output metadata moves to `ResultScreen`

## 5. MetadataDiffCard

- [x] 5.1 Create `MetadataDiffCard.kt` with a `MetadataDiffCard(sourceProbe: MediaProbe, outputProbe: MediaProbe)` Composable
- [x] 5.2 Implement curated tag merging: same key set as `ProbeCard` (creation_time, location, rotation, video codec+dims, com.* tags); collect output values by key using the same merge strategy
- [x] 5.3 Render each curated row with three cells: key, source value, output value; mark a row as "changed" when the output value differs or is absent
- [x] 5.4 Style changed/missing rows distinctly (e.g. `MaterialTheme.colorScheme.error` text on the output cell)
- [x] 5.5 Implement the summary banner: count changed/missing rows; render "✓ All N tags preserved" in `primary` colour when count is zero, otherwise "⚠ N tag(s) missing or changed" in `error` colour
- [x] 5.6 Implement overflow expand/collapse: collect remaining tags from both probes (keys not in curated set); show both source and output values; apply per-row status logic

## 6. ResultScreen

- [x] 6.1 Create `ResultScreen.kt` with a `ResultScreen(navController, viewModel)` Composable; observe `uiState` from the shared ViewModel
- [x] 6.2 Show the output absolute path label (`CutState.Saved.outputAbsolutePath` if non-null, otherwise a fallback message)
- [x] 6.3 Play the cut output video: construct a `File` from the output absolute path (or re-use the cache file still referenced by `CutState`) and pass it to `rememberVideoPlayer` / `VideoPreview`
- [x] 6.4 Render `MetadataDiffCard` with `uiState.sourceProbe` and `uiState.outputProbe` (guard with null checks; show a placeholder if either is null)
- [x] 6.5 Add a back button (top-left `IconButton` with a back arrow) that calls `navController.popBackStack()`

## 7. Validation

- [x] 7.1 Build the project (`./gradlew.bat :app:assembleDebug`) and confirm it compiles clean
- [x] 7.2 Install on the emulator and run through the full flow: pick → trim → cut → verify result screen shows output path, cut video, and diff card
- [x] 7.3 Confirm back navigation returns to the trim screen with handles intact
- [x] 7.4 Confirm that a second cut from the same trim screen navigates to a fresh result screen (no stale data)
- [x] 7.5 Verify the source path label on the trim screen shows the absolute path for a known file
