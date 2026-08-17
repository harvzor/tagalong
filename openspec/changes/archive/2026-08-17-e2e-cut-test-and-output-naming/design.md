## Context

`:app` currently has no instrumented tests. `CutViewModel.runCut()` names the output `"tagalong-${System.currentTimeMillis()}.mp4"` and does not track the source's display name. `PickedSource` holds `uri`, `file`, and `durationMs` only. The engine contract tests in `:engine` cover metadata preservation thoroughly but do not exercise the app's own wiring (`materializeToCache`, display-name derivation, the `DateTakenStore` call from the app layer).

The system photo picker (`PickVisualMedia` contract) is a separate OS process; Espresso cannot interact with it. The emulator (`Pixel_7_API_34`) runs the AOSP Photopicker from `com.android.providers.media.module`. The test fixture lives in module assets, not in MediaStore.

## Goals / Non-Goals

**Goals:**
- Output files carry a human-readable, predictable name that encodes source identity and trim bounds.
- A single instrumented test covers the full path from picker to saved output, including the system photo picker, without stubbing any intent.
- The predictable output name lets the test locate the saved file in MediaStore without changing `CutUiState`.

**Non-Goals:**
- UIAutomator selectors are not hardened against system picker redesigns; robustness at that level is deferred.
- The test does not cover re-encode mode (step 2 of the roadmap).
- Parallel or multi-source flows are out of scope.

## Decisions

### D1 — Output display name derived at save time in `CutViewModel`

The display name is built from `source.originalDisplayName` (base, extension stripped), `state.startMs`, and `state.endMs` using `formatTimestamp(ms)`:

```
%02d-%02d-%02d-%03d  →  HH-MM-SS-mmm
```

Example: `xiaomi-poco-x5_from_00-00-00-500_to_00-00-03-500.mp4`

**Alternative considered — build name at pick time:** The name can't be built at pick time because `startMs`/`endMs` aren't known then. Rejected.

**Alternative considered — expose name via `CutState.Saved`:** Not needed; the test locates the file by querying MediaStore with `DISPLAY_NAME = computedName`. Adding the name to the state would couple UI to a field it doesn't display. Deferred unless a future screen needs to show the saved name.

### D2 — `originalDisplayName` sourced from ContentResolver at pick time

`CutViewModel.onVideoPicked` queries `MediaStore.MediaColumns.DISPLAY_NAME` on the picked Uri and stores the result in `PickedSource.originalDisplayName`. Fallback if the query returns null: `"video"`.

**Alternative considered — parse the Uri path:** Content Uris from PickVisualMedia are opaque (`content://media/external/video/media/42`); path parsing is unreliable. Rejected.

### D3 — E2E test structure: seed → launch → UIAutomator → Compose → probe

```
@Before  MediaStoreSeeder.insert(fixture)  → get insertedUri
Test:
  1. createAndroidComposeRule<MainActivity> → launch app
  2. composeTestRule.onNodeWithText("Pick video").performClick()
  3. PhotoPickerRobot.selectFirstItem(device)   ← UIAutomator
  4. composeTestRule.waitUntil { "Cut and save" is visible }
  5. composeTestRule.onNodeWithText("Cut and save").performClick()
  6. composeTestRule.waitUntil { "Saved" text is visible }
  7. MediaStoreSeeder.findByDisplayName(expectedName) → outputFile
  8. MetadataAssertions.sourceTagsSubsetOfOutput(source, output)
@After   MediaStoreSeeder.delete(insertedUri, outputUri)
```

**Alternative considered — inject a file Uri, bypass the picker:** Defeats the goal of testing the full path through the system picker. Rejected.

### D4 — UIAutomator picker interaction: click first item in the grid

The fixture is inserted into MediaStore immediately before the picker opens, so it appears as the most recently added item (first in the grid). `PhotoPickerRobot` waits for the picker package to appear, then clicks the first `clickable(true)` child of the `RecyclerView`.

**Known fragility:** If the emulator accumulates other videos between test runs, the fixture may not be first. Mitigation in `@Before`: delete any previously-seeded fixture before reinserting so there is exactly one copy.

**Alternative considered — match by display name / content description:** The AOSP Photopicker renders thumbnails without exposing the filename as an accessible text node. Rejected as unreliable.

**Open question recorded:** The "Add" button label may be "Add" or "Done" depending on Android version and picker build. `PhotoPickerRobot` tries both in order. The actual label on `Pixel_7_API_34` can be confirmed by running `adb shell uiautomator dump` while the picker is open; see the Open Questions section.

### D5 — Test lives in `:app` not a separate `:app-test` module

The test requires the real `Application` instance and the real `CutViewModel` via `ActivityScenario`. Placing it in `app/src/androidTest/` is the natural home; no new module is needed.

### D6 — No change to `CutState.Saved`

The test finds the output by querying `MediaStore.Video.Media` with `DISPLAY_NAME = expectedName` after the cut completes. `CutState.Saved` continues to carry only `galleryDateMillis`. Avoids coupling state to a concern the UI does not currently surface.

## Risks / Trade-offs

- **UIAutomator picker coupling** → The picker's internal widget tree can shift between OS updates. The first-item strategy decouples from node identity but not from `RecyclerView` existence. If the picker is redesigned, `PhotoPickerRobot` will need updating. *Mitigation*: keep the robot isolated so updates are contained.

- **MediaStore scan lag** → After `MediaStoreSeeder.insert`, the picker might not immediately show the item if a scan is in progress. *Mitigation*: insert via `ContentResolver.insert` (synchronous; item is immediately visible to the picker without a scan).

- **Output not yet indexed when the test queries** → After save, `DateTakenStore.registerAndReadBack` already inserts the file into MediaStore; the test can query immediately after `CutState.Saved` is observed.

- **Long cut duration on emulator** → swiftshader_indirect GPU + ffmpeg-kit on the emulator can be slow. The fixture is short (≈ 4 s); the test timeout for `waitUntil` should be set generously (60 s for the cut step).

## Open Questions

- **Picker "Add" vs "Done" button label on API 34:** Confirmed by UI dump or first test run. `PhotoPickerRobot` tries `By.text("Add")` first, then `By.text("Done")`; whichever hits first wins. Update the robot after first run if neither matches.
