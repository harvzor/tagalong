## 1. Canonical sample-video corpus

- [ ] 1.1 Rename the repository directory from `fixtures/` to `sample-videos/` and place both `xiaomi-poco-x5.mp4` and `google-pixel-10a.mp4` in it.
- [ ] 1.2 Configure the `androidTest` asset source set for `:engine` and `:app` to package `sample-videos/`, then remove the duplicated module-local video assets.
- [ ] 1.3 Update `scripts/push-fixtures-to-emulator.sh` to use `sample-videos/` by default while preserving its explicit directory override and existing supported-extension filtering.
- [ ] 1.4 Update active repository documentation to describe `sample-videos/` as the canonical corpus and explain how adding a video expands the test matrix; leave archived OpenSpec history unchanged.

## 2. Fixture discovery and engine contract coverage

- [ ] 2.1 Add deterministic discovery of supported packaged sample videos, including extension filtering, sorted order, empty-corpus diagnostics, and duplicate/ambiguous-name validation.
- [ ] 2.2 Update fixture materialization, cache paths, and output paths to use each sample's filename and prevent cross-sample reuse.
- [ ] 2.3 Refactor the engine contract harness to probe and hash each sample independently, derive a valid cut interval from its duration, and run both lossless and re-encode assertions for every sample.
- [ ] 2.4 Include the sample filename and cut mode in all engine assertion messages and generated output names while retaining source-tag, orientation, source-immutability, and gallery-date assertions.

## 3. App end-to-end coverage

- [ ] 3.1 Update the app instrumentation helpers to seed and materialize a selected sample by its discovered filename rather than relying on `xiaomi-poco-x5.mp4` constants.
- [ ] 3.2 Update file-picker selection to target the current sample's unique filename or stem and fail clearly when the selection is ambiguous or missing.
- [ ] 3.3 Run the app pick-to-cut-to-save metadata flow for every discovered sample, returning to a clean starting state between samples.
- [ ] 3.4 Clean up each sample's MediaStore rows, cached files, and outputs independently and include the sample identity in end-to-end failures.

## 4. Verification

- [ ] 4.1 Build the instrumented-test artifacts and verify both test APKs contain the Xiaomi and Pixel 10a sample videos from `sample-videos/` without production APK packaging.
- [ ] 4.2 Run the engine instrumented suite on the connected wireless device and verify that both samples execute in both modes; preserve the existing metadata assertions and identify any known re-encode rotation-gap failures rather than weakening them.
- [ ] 4.3 Run the app end-to-end metadata suite on the connected wireless device and verify that both samples are selected, cut, and checked independently.
- [ ] 4.4 Run `openspec validate --change "add-multi-device-sample-videos"` and confirm the final repository status contains only the intended implementation and sample-video changes.
