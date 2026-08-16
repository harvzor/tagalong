## Why

Tagalong's entire reason to exist is a single guarantee: trim a video without losing the metadata that says when and where it was shot — including the gallery date that most editors silently reset. Before building any app around that promise, we must prove it is technically achievable on a real Android device. Desktop ffmpeg already confirms the lossless cut preserves every file-level tag; what remains unproven is whether it works through an on-device engine and whether the gallery date (`MediaStore.DATE_TAKEN`) can be preserved at all.

There are two candidate engines and no way to choose from documentation alone. Jetpack **Media3 Transformer** would be strictly better if it works (native, Apache-2.0, small, hardware-accelerated, same family as our future UI), but its metadata fidelity is unproven. **ffmpeg-kit** (antonkarpenko fork) is the known-good fallback (proven by the LibreCuts app) but is GPL, ships a large native binary, and is a single-maintainer fork of a retired project. We resolve this by running both against one identical test and one real fixture.

## What Changes

- Introduce a **cut-engine bake-off spike**: the smallest possible Android module whose only code is an instrumented test, run against a real device/emulator.
- Establish the behavioral contract the cut engine must satisfy — the permanent acceptance bar that outlives the spike: no source tag lost, gallery date preserved, and the guarantee holding identically in both lossless and re-encode modes.
- Validate that bar against **two engines** — Media3 Transformer (Arm A) and antonkarpenko ffmpeg-kit (Arm B) — using one shared test harness, one real fixture (`xiaomi-poco-x5.mp4`), and one assertion set.
- Produce a decision: which engine passes with the least baggage becomes tagalong's cut engine; the loser is deleted.
- Deliberately **out of scope**: any app UI, file-picker, trim controls, navigation, the full production toolchain, and shipping ffmpeg-kit-next (the long-term target, deferred because it has no prebuilt package and requires building the AAR from source).

## Capabilities

### New Capabilities
- `metadata-preserving-cut`: The behavioral contract for cutting a video while preserving its metadata — the tags that must survive (creation date, GPS location, camera make/model, orientation), the separate obligation to preserve the gallery date via `MediaStore.DATE_TAKEN`, the "no source tag lost" definition of preservation, and the rule that the guarantee holds identically in both lossless stream-copy and re-encode modes. This spike validates the contract; later changes build the app against it.

### Modified Capabilities
<!-- None. This is the first behavioral capability in the project. -->

## Impact

- **New files**: an Android module (spike-scoped) with an instrumented test suite; a real-video test fixture at `xiaomi-poco-x5.mp4` (already added).
- **Dependencies evaluated**: `androidx.media3:media3-transformer`/`media3-muxer` and `com.antonkarpenko:ffmpeg-kit-full-gpl`. Only the winning dependency survives into the app.
- **Environment**: requires a running emulator or a physical device reachable over adb (adb is already available). No production build toolchain is set up by this change.
- **Downstream**: the engine decision and the validated contract feed directly into the subsequent environment-setup and app-build changes. A negative result (neither engine preserves the gallery date) would force a rethink of the product before any app code is written — which is precisely why this spike comes first.
