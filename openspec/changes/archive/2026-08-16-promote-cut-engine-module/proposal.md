## Why

The cut engine proven by the bake-off — the ffmpeg command shapes, the metadata reader, and the save-to-gallery flow — lives entirely in `cutdebug/src/androidTest/`. That is test-only code: it never ships and cannot be called from an app. Before any app feature can be built, this proven engine needs to live in a real, shippable module whose behavior is guarded by the same contract test that validated it. This is the foundation step (Step 0) that unblocks every later app feature.

## What Changes

- Introduce a new `:engine` Gradle module (`com.android.library`) holding the production engine in `src/main`.
- **Copy** (not move) the proven engine code from `cutdebug/src/androidTest/` into `engine/src/main/`: `FfmpegCutEngine`, the `CutEngine` interface + `CutMode`, `MetadataReader`, `DateTakenStore`, and their support types.
- **Copy** the contract test (`CutEngineContractTest` + `FfmpegCutEngineTest`, plus `MetadataAssertions`, `FileAssertions`, `TestFixtures`, and the fixture asset) into `engine/src/androidTest/`, re-pointed at the production engine so the contract now guards shipping code.
- Promote `ffmpeg-kit-full-gpl` from an `androidTestImplementation` dependency to a production `implementation` dependency of `:engine`. **BREAKING for licensing**: the shipped artifact now contains GPL code (see Impact + design open decision).
- **Freeze `cutdebug`**: it is not edited. It stays as the frozen bake-off record (both engines, its own contract test) and remains runnable as a reference. It is slated for eventual deletion, but not in this change.
- Register `:engine` in `settings.gradle.kts`.

Explicitly out of scope: any `:app` module, any UI, any file picker, the Media3 engine, and the re-encode rotation-signal fix. Those are later steps.

## Capabilities

`skip_specs: true` — this is a structural refactor. The engine's observable behavior does not change; it is governed by the existing, unchanged `metadata-preserving-cut` spec. The contract test is copied verbatim (re-pointed only), so the requirements it enforces are identical. No spec-level behavior is added, modified, or removed, so no delta spec is written.

### New Capabilities
None.

### Modified Capabilities
None. The behavioral contract in `openspec/specs/metadata-preserving-cut/spec.md` continues to govern the relocated engine without change.

## Impact

- **New module**: `engine/` (`build.gradle.kts`, `src/main/`, `src/androidTest/`), added to `settings.gradle.kts`.
- **Dependencies**: `ffmpeg-kit-full-gpl:2.1.0` becomes a production dependency of `:engine`. The `-full-gpl` variant is **GPL**, so a shipped app linking `:engine` inherits GPL obligations. This was harmless while ffmpeg-kit was test-only; it becomes a live licensing decision here. Flagged as an open decision in design.md (keep `-full-gpl` vs. switch to an LGPL variant); resolving it is not required to complete this structural change, but it must be resolved before an app ships.
- **Duplicated code**: the engine + contract test now exist in two places (frozen `cutdebug`, live `:engine`). Acceptable because `cutdebug` is frozen — a frozen copy cannot drift — and both copies carry their own passing contract test.
- **Toolchain**: `:engine` uses the same stack as `cutdebug` (Gradle 9.5, AGP 9.2.1, built-in Kotlin 2.3.20 — no `org.jetbrains.kotlin.android` plugin, no `kotlinOptions {}` block).
- **Test cost unchanged**: engine tests remain instrumented (`connectedAndroidTest`, emulator required) because ffmpeg-kit is native — no new JVM test path is created or expected.
- **Known gap carried, not fixed**: `FfmpegCutEngine` re-encode mode still loses the rotation signal. It is copied as-is; the contract test's re-encode case is expected to fail exactly as it does today. This change does not address it.
