## 1. Baseline before anything changes

- [x] 1.1 Record the current release asset size for the newest published tag (`gh api repos/harvzor/tagalong/releases --jq '.[0].assets[] | "\(.name) \(.size/1048576|floor)MB"'`) as the before figure.
- [x] 1.2 Run a local `./gradlew :app:assembleRelease` and record the output layout and per-file sizes, with `splits` absent. This is the reference for "what changed" in group 5 and for confirming the collection pattern still matches reality.
- [x] 1.3 Confirm the emulator's architecture matches a planned artifact: `adb -s emulator-5554 shell getprop ro.product.cpu.abilist` is expected to report `arm64-v8a`. Record the result — group 4 depends on it.
- [x] 1.4 Record the current pass state of `./gradlew :app:connectedDebugAndroidTest`, `:engine:connectedAndroidTest`, and `:engine:testDebugUnitTest` while the build is still unsplit, so a regression in group 4 is distinguishable from a pre-existing failure.

## 2. Packaging fix first (behaviour-preserving on its own)

Lands independently of the split configuration: with exactly one build output it behaves identically to today, so it removes the silent-overwrite hazard without taking any release risk.

- [x] 2.1 In `Dockerfile`, scope the APK collection step to the variant being exported so it cannot select anything under `app/build/outputs/apk/androidTest/`.
- [x] 2.2 Derive a per-artifact identifier from each output rather than hard-coding a count or a fixed list, and write each to `tagalong-${VERSION}-<identifier>.apk`. Confirm no two outputs can reach the same destination path.
- [x] 2.3 Map identifiers to canonical spellings via an explicit mapping — `arm64_v8a` → `arm64-v8a`, `x86_64` → `x86_64`, combined → `universal`. Do **not** use a blanket underscore-to-hyphen substitution; it would emit `x86-64`.
- [x] 2.4 Verify the fix against a deliberately multi-output condition while the build still emits one APK, by planting a second APK file at the path the collection step selects and confirming both survive to `out/` with distinct names. Remove the planted file afterwards.
- [x] 2.5 Re-run the ordinary single-output build and confirm the output is unchanged apart from the new filename, and that no `androidTest` APK appears in `out/` even when `app/build/outputs/apk/androidTest/` exists on disk.

## 3. Split configuration

- [x] 3.1 In `app/build.gradle.kts`, add `splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = true } }`. Keep `reset()` — without it the default ABI list is retained alongside the explicit one.
- [x] 3.2 Confirm `isMinifyEnabled` remains `false`, and leave a one-line comment recording that minification is deliberately deferred to a separate change with a verification precondition, so it is not flipped casually later.
- [x] 3.3 ~~Confirm no `ndk { abiFilters }` block was introduced~~ **AMENDED during apply — superseded by 3.6.** The premise ("`splits.abi` governs the architecture set") was falsified by measurement: `include()` decides which *splits* are generated but does not bound the `universal` artifact, which merged all four ABIs from the AAR (243 MB, 32-bit included). `ndk.abiFilters` is therefore *required* to satisfy 5.3/5.4. The legitimate concern behind this guard — two lists drifting apart — is resolved by 3.6's single source of truth rather than by omitting a required knob.
- [x] 3.4 Confirm all three outputs share one `versionCode` derived from `-PappVersionName`, with no per-architecture offset.
- [x] 3.5 List the actual release output paths and sizes rather than assuming them — AGP may emit under `apk/<abi>/release/` or flat under `apk/release/`. Adjust the group 2 collection pattern to match what is really there, not what was predicted.
  Measured: **flat** under `apk/release/`; 70 / 76 / 243 MB. Group 2's leaf-directory search already handles both layouts, so no pattern change was needed.
- [x] 3.6 Declare the shipped architecture set exactly once (`val shippedAbis = listOf("arm64-v8a", "x86_64")`) and feed it to **both** `ndk.abiFilters` (in `defaultConfig`, bounding the merged native-lib set and hence the `universal` APK) and `splits.abi.include` (deciding which per-architecture splits are generated). Adding or removing an architecture must remain a one-line change with no second edit to remember.

## 4. Settle the debug-loop question before anything is published

Design flags this as resolving by execution, not argument. Do not proceed to group 5 until 4.1 is green.

- [x] 4.1 Re-run `./gradlew :app:connectedDebugAndroidTest` and `:engine:connectedAndroidTest` on the `arm64-v8a` emulator with the split configuration in place, and confirm both still install and pass against the 1.4 baseline.
- [x] 4.2 If the debug loop regresses, restrict the split configuration to release variants so `debug` keeps a single unsplit APK, re-run 4.1, and record the chosen shape and its reason in `design.md`.
  <!-- NOT TRIGGERED: 4.1 stayed green, so the release-only restriction was not applied. Splits remain global (they also affect debug); verified harmless. 4.2 evaluated, not implemented. -->
- [x] 4.3 Run `./gradlew :engine:testDebugUnitTest` — host-side, no emulator — and confirm it stays green under the 256 MB heap pin.

## 5. Verify the published artifact set against the spec

Each item here maps to a scenario in the `release-artifacts` delta.

- [x] 5.1 `./gradlew :app:assembleRelease` produces exactly three release APKs — `arm64-v8a`, `x86_64`, `universal` — and record each file's size as the after figure against 1.1.
- [x] 5.2 Inspect each APK's native library listing. The `arm64-v8a` artifact contains only `arm64-v8a`; the `x86_64` artifact only `x86_64`.
- [x] 5.3 Confirm the `universal` artifact contains **exactly** the two shipped architectures and no others — in particular that it is not broader than the sum of the separate artifacts.
- [x] 5.4 Confirm no artifact contains `armeabi-v7a` or `x86` native libraries. This is the check that the 32-bit removal is real and complete, not just an absent split.
- [x] 5.5 Install the `arm64-v8a` artifact on the emulator and complete a cut end-to-end. Confirm the output carries GPS location and `creation_time` — the preservation contract must hold on the artifact users actually install, which no debug build proves.
- [x] 5.6 Install the `universal` artifact on the same emulator over a clean state and complete a cut. Confirm the surviving tag set is identical to 5.5, satisfying "cutting behaves identically regardless of which artifact was installed".
- [x] 5.7 Confirm that installing an architecture-mismatched artifact is refused at install time rather than installed and left to fail. Either demonstrate it on a mismatched target or state in the release notes which mismatch was verified by reasoning only.

## 6. Documentation

- [x] 6.1 `README.md` install section: name all three artifacts, say in ordinary language which devices each is for, and name `universal` as the answer for a reader who does not want to decide.
- [x] 6.2 Add to the same section: remove an existing install before switching artifact, so a switch is not mistaken for a downgrade failure.
- [x] 6.3 State that an install rejected as incompatible means the device is unsupported, not that the download is damaged — the sentence a 32-bit user needs.
- [x] 6.4 Update the Building section's stated output path, and the signed-release filename in the Releases section, to the names the build now produces. Every filename the README promises must be one a contributor can find.
- [x] 6.5 Keep the install guidance short enough to read once — three rows and two instructions, not an explanation of what a CPU architecture is.
- [x] 6.6 `AGENTS.md`: record the shipped architecture matrix, that 32-bit is excluded and why, and that per-architecture publishing does not touch the preservation contract. Note that a future Play move replaces this artifact set with a single AAB.

## 7. Confirm the release pipeline end-to-end

The workflow file is expected to need no change. Confirm by observation, not inspection.

- [x] 7.1 Confirm `.github/workflows/release-build.yml` still globs `out/*.apk` and needs no change given three assets.
- [ ] 7.2 Cut a release candidate tag and confirm the resulting GitHub Release carries three distinct, signed assets with the version in each filename.
- [ ] 7.3 Confirm no expected asset is missing from the release — treat a missing artifact as an incomplete run rather than a success.
- [ ] 7.4 Record the before/after download size for the `arm64-v8a` artifact in the release notes, and repeat the uninstall-before-switch note there, since existing installs are of the old combined APK.
