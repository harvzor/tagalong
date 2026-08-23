## Context

See `proposal.md — Why` for motivation.

The app currently has one Composable entry point (`CutScreen`) rendered directly inside `MainActivity.setContent`. There is no navigation graph. The cut pipeline lives in `CutViewModel` (an `AndroidViewModel`), which owns a single `StateFlow<CutUiState>`. After a cut `DateTakenStore.registerAndReadBack` returns `Long?` (the gallery date); the output's storage path is not captured. `ProbeCard` renders a single `MediaProbe`; there is no diff-aware variant.

## Goals / Non-Goals

**Goals:**
- Introduce Navigation Compose as the navigation layer (extensible for future screens).
- Split `CutScreen` into `TrimScreen` (Window 1) and `ResultScreen` (Window 2).
- Surface the full absolute storage path of both source and output.
- Replace the stacked two-card probe display with a unified diff card on `ResultScreen`.
- Keep `CutViewModel` as the single source of truth — no new ViewModels.

**Non-Goals:**
- Shared-element transitions or animated navigation (plain crossfade / slide is sufficient).
- Deep-link support for either route.
- Any change to `:engine` or `:cutdebug`.
- Changing the curated tag set (that is a `probe-viewer` spec concern, not this change).

## Decisions

### D1 — Navigation Compose with two routes

Add `androidx.navigation:navigation-compose` to `:app`. Define a `NavHost` in `MainActivity` with two routes: `"trim"` and `"result"`.

**Why not state-driven swap inside one Composable?** A conditional `if (cutState is Saved) ResultContent() else TrimContent()` works for two screens but degrades as screens multiply — no back stack, no transitions, no type-safe arguments. Navigation Compose is the idiomatic Android foundation and the cost at two routes is low (one extra dependency, one `NavHost` wrapper).

**ViewModel sharing**: Both `TrimScreen` and `ResultScreen` call `viewModel<CutViewModel>()` without a scope override. This resolves to the same Activity-scoped instance automatically — no extra wiring.

**Back stack**: `TrimScreen` is the start destination. After a successful cut `TrimScreen` triggers `navController.navigate("result")`. `ResultScreen` calls `navController.popBackStack()` for its back action. Trim state survives because the ViewModel is Activity-scoped and never reset by navigation.

**Navigation trigger**: A `LaunchedEffect(cutState)` in `TrimScreen` fires `navController.navigate("result")` when `cutState` transitions to `CutState.Saved`. The navigate call is `launchSingleTop = true` so repeated rapid taps do not stack multiple result screens.

### D2 — Source absolute path: construct from storage root + relative path

`PickedSource` gains an `absolutePath: String?` field. In `CutViewModel.onVideoPicked`, after resolving `RELATIVE_PATH` and `DISPLAY_NAME` from the MediaStore query, construct:

```
absolutePath = Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath + displayName
```

This is only set when `RELATIVE_PATH` is non-null (same condition that already populates `displayPath` with the full relative form). `Environment.getExternalStorageDirectory()` returns `/storage/emulated/0` on virtually all Android devices; the construction is reliable within the minSdk 31 target.

**Why not query `DATA` on the `ACTION_OPEN_DOCUMENT` URI?** The document URI (`content://com.android.providers.media.documents/document/video:…`) is not a MediaStore row URI, so querying `MediaStore.MediaColumns.DATA` on it is not guaranteed to succeed. Construction from `RELATIVE_PATH` is more portable.

### D3 — Output absolute path: reuse `DateTakenStore`'s existing legacyPathFor query

`DateTakenStore.registerAndReadBack` currently returns `Long?` and calls `legacyPathFor` internally only for rescanning. Change the return type to a data class:

```kotlin
data class SaveResult(val dateTakenMillis: Long?, val absolutePath: String?)
```

`registerAndReadBack` captures the `legacyPathFor` result (already queried for rescan) and includes it in `SaveResult`. Callers that previously used the `Long?` return adjust to `.dateTakenMillis`.

`CutState.Saved` gains `outputAbsolutePath: String?` alongside `galleryDateMillis`.

`legacyPathFor` queries `MediaStore.Video.Media.DATA` on the MediaStore URI returned by `resolver.insert` — this IS a MediaStore row URI, so the `DATA` column is reliably populated (even though `DATA` is deprecated for scoped storage picks, it works for rows the app itself inserted).

### D4 — MetadataDiffCard replaces the stacked two-card display

A new `MetadataDiffCard(sourceProbe: MediaProbe, outputProbe: MediaProbe)` Composable is written in `MetadataDiffCard.kt`. It:
- Merges both probes into the same curated tag set as `ProbeCard` (same key ordering, same `com.*` filter, same overflow expand/collapse).
- Renders each row with three cells: key, source value, output value.
- Compares values by string equality; a row is "changed" when values differ or the output value is absent (`—`).
- Shows a summary banner at the top using `MaterialTheme.colorScheme.primary` (all preserved) or `MaterialTheme.colorScheme.error` (any missing/changed).

`ProbeCard` is retained unchanged for the source-only card on `TrimScreen`.

### D5 — File layout

| Before | After |
|---|---|
| `CutScreen.kt` | `TrimScreen.kt` (Window 1) |
| _(new)_ | `ResultScreen.kt` (Window 2) |
| `ProbeCard.kt` | `ProbeCard.kt` (unchanged) |
| _(new)_ | `MetadataDiffCard.kt` |
| `MainActivity.kt` | `MainActivity.kt` — wraps in `NavHost` |
| `CutUiState.kt` | `CutUiState.kt` — `PickedSource.absolutePath`, `CutState.Saved.outputAbsolutePath` |
| `CutViewModel.kt` | `CutViewModel.kt` — path construction, `SaveResult` adapter |
| `DateTakenStore.kt` (`:engine`) | `DateTakenStore.kt` — `SaveResult` return type |

## Risks / Trade-offs

- **`DATA` column deprecation**: `legacyPathFor` queries `DATA` which is deprecated under scoped storage. It continues to work for app-inserted rows on API 29–36 and is already present in the codebase for rescanning. If it returns `null` the output path field is `null` and the UI shows a fallback (no crash).
- **`Environment.getExternalStorageDirectory()` assumption**: Constructed paths assume primary external storage. On devices with multiple volumes where the user picks from a secondary SD card, the path will be wrong. Given the `ACTION_OPEN_DOCUMENT` flow and the target device profile this is an acceptable edge case; no fallback is needed (the display degrades to the gallery-relative path already in `displayPath`).
- **Navigation Compose version alignment**: `navigation-compose` must match the Compose BOM in use (`2026.04.01`). Pin via the BOM to avoid version skew.

## Open Questions

None — all decisions above are resolved within the existing spec and implementation constraints.
