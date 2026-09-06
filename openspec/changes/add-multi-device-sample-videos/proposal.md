## Why

The metadata-preservation tests currently assume a single Xiaomi fixture and keep duplicate copies inside each Android test module. A real Pixel 10a video is now available, but it is not part of the instrumented test matrix, so device-specific metadata, orientation, and container behavior are not covered. The repository needs a canonical, extensible sample-video corpus so additional device videos can be added without changing test logic.

## What Changes

- Rename the repository-level canonical video directory from `fixtures/` to `sample-videos/`.
- Add `google-pixel-10a.mp4` alongside the existing Xiaomi sample video.
- Package the canonical sample-video directory as Android instrumented-test assets for both `:engine` and `:app`, removing duplicate module-local video copies.
- Make engine contract tests discover and exercise every supported sample video in both lossless and re-encode modes.
- Make the app end-to-end metadata test exercise every discovered sample video and select each one by its real filename.
- Use per-video cache files, outputs, cleanup, and assertion labels so failures identify the originating device sample.
- Update the emulator seeding script and active documentation to use `sample-videos/` and describe the add-a-video workflow.
- Preserve the existing Xiaomi coverage; the Pixel 10a video is additional coverage, not a replacement.

## Capabilities

### New Capabilities

- `test-fixtures`: A canonical, discoverable repository sample-video corpus and a multi-video instrumented-test matrix for validating metadata-preserving cuts across device-originated files.

### Modified Capabilities

<!-- No product capability requirements change. This change adds repository test coverage and test-fixture behavior. -->

## Impact

- Repository layout: the canonical video directory and its active documentation references change.
- Gradle Android-test asset configuration for `:engine` and `:app` changes.
- Engine and app instrumented-test helpers and runners become fixture-driven instead of single-file-specific.
- `scripts/push-fixtures-to-emulator.sh` changes its default source directory; its public script name remains unchanged unless implementation identifies a compatibility concern.
- The frozen `:cutdebug` module is not modified.
- No production app API or user-facing runtime behavior changes.
