## Why

The `:cutdebug` module was a side-by-side bake-off harness used to evaluate ffmpeg-kit vs Media3 Transformer. That evaluation is complete, its findings are fully archived in `openspec/changes/archive/2026-08-16-cut-engine-bakeoff/`, and the module has been frozen ("do not touch") ever since. Removing it simplifies the build structure ahead of adding a Docker-based release pipeline, where the module added pointless complexity without contributing to the shipped APK.

## What Changes

- Delete the `cutdebug/` directory and all its contents
- Remove `include(":cutdebug")` from `settings.gradle.kts`
- Root `build.gradle.kts` plugin declarations are unchanged — `com.android.library` is still needed by `:engine`

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
<!-- None — this is a pure deletion of a frozen, non-shipping module. No spec-level behavior changes. -->

## Impact

- `settings.gradle.kts`: one line removed
- `build.gradle.kts` (root): unchanged — `com.android.library` is still required by `:engine`
- `cutdebug/` directory: deleted entirely
- `:app` and `:engine` are unaffected — neither depends on `:cutdebug`
- The bake-off findings and decision rationale remain in the archive; no information is lost
