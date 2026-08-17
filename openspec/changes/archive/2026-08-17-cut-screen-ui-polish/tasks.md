## 1. Data model — add displayPath to PickedSource

- [x] 1.1 Add `displayPath: String` field to `PickedSource` in `CutUiState.kt` (design D1)
- [x] 1.2 Extend the `resolver.query(...)` column array in `CutViewModel.onVideoPicked` to include `MediaStore.MediaColumns.RELATIVE_PATH` alongside `DISPLAY_NAME` (design D2)
- [x] 1.3 Build `displayPath` in `onVideoPicked`: concatenate `relativePath + displayName` when `RELATIVE_PATH` is non-null, else use `displayName` alone; pass the result into the `PickedSource` constructor

## 2. CutScreen layout changes

- [x] 2.1 Wrap the no-source branch content in a centered sub-`Column` (`fillMaxSize`, `verticalArrangement = Arrangement.Center`, `horizontalAlignment = Alignment.CenterHorizontally`) so the empty-state button sits in the natural thumb zone (design D5)
- [x] 2.2 Apply `Modifier.fillMaxWidth()` to the empty-state "Pick video" `Button` (design D3)
- [x] 2.3 Apply `Modifier.fillMaxWidth()` to the "Cut and save" `Button` (design D3)
- [x] 2.4 Change "Pick a different video" from `Button` to `OutlinedButton` and apply `Modifier.fillMaxWidth()` (design D4)
- [x] 2.5 Apply `Modifier.fillMaxWidth()` to `VideoPreview`; add a `heightIn(max = 320.dp)` (or similar) constraint so a tall portrait clip does not push the controls below the fold (design D6)
- [x] 2.6 Add a `Text(source.displayPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)` above the `VideoPreview` in the source-loaded branch (spec: cut-workflow D2)

## 3. Verification

- [x] 3.1 Build and install on the Pixel_7_API_34 emulator; visually confirm all five layout changes (centered empty state, full-width buttons, outlined secondary button, wider preview, path label)
- [x] 3.2 Pick `xiaomi-poco-x5.mp4` and confirm the path label shows the gallery-relative path (or filename fallback if the Google Photopicker redacts `RELATIVE_PATH`)
