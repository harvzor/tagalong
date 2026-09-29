## Why

Every Tagalong release is a single 243 MB APK. That APK is not a big app — it is four copies of the same app, one per CPU architecture, stapled together. `ffmpeg-kit-full-gpl` bundles prebuilt native libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`, 214.7 MB of `.so` files in total, and a given device can execute exactly one of those folders. Android installs the whole 243 MB and reads the slice it needs; the rest sits on the user's storage permanently.

Splitting the release along architecture lines is how the rest of this app's category handles it (LibreCuts ships the same ffmpeg-kit dependency as three per-architecture artifacts, 50–86 MB each). Doing it here, plus dropping the two 32-bit architectures that `minSdk` 31 makes unreachable in practice, takes the download a modern phone sees from 243 MB to ~70 MB.

There is also a latent defect that this change makes active rather than theoretical: the release pipeline derives one destination filename from a recursive search over build outputs, so it cannot survive multiple artifacts. It is correct only while the build produces exactly one APK.

## What Changes

- Release builds produce **three installable artifacts** per release instead of one: `arm64-v8a`, `x86_64`, and a `universal` covering both.
- **BREAKING** — the 32-bit architectures (`armeabi-v7a`, `x86`) are no longer shipped. No artifact in the release will install on a 32-bit-only device; Android rejects it at install time with a clear incompatibility message rather than installing and failing later.
- `isMinifyEnabled` stays `false`. Code shrinking is deliberately excluded and deferred to a separate change; see Design.
- **BREAKING** release artifact naming: `tagalong-<version>.apk` becomes `tagalong-<version>-<architecture>.apk`, including a `universal` suffix for the combined artifact.
- Fix the packaging step so every produced artifact reaches the release. Today each output is copied to the same destination name, so with more than one APK all but one would be silently overwritten and the release would publish a single, mislabelled architecture.
- README install section resolves "which file do I download" explicitly, in one decision that does not require the reader to know what an architecture is.

## Capabilities

### New Capabilities

- `release-artifacts`: the set of installable artifacts published per release — which architectures each covers, that each artifact is installable on the architecture it names, that a `universal` artifact exists so no user has to identify their own device correctly, and the identity and count of published assets.

### Modified Capabilities

- `docker-build`: the `VERSION` build argument currently specifies a single output filename, which cannot name multiple artifacts. Naming becomes per-artifact, and output enumeration becomes collision-free and scoped so it can never pick up test-build outputs.
- `gha-release-build`: tag-derived naming and asset upload now cover the whole artifact set rather than one file.
- `repository-readme`: the install requirement currently promises "no ambiguity about which file to download" while publishing exactly one file. With three artifacts published, the README must itself resolve the choice, name the architectures each artifact serves, and give a recovery step for a wrong pick.

## Impact

**Code and config**
- `app/build.gradle.kts` — `splits { abi { … } }` block: enable, `reset()`, `include("arm64-v8a", "x86_64")`, `universalApk = true`. `isMinifyEnabled` unchanged at `false`.
- `Dockerfile` (export step, lines ~89–95) — per-architecture destination names; enumeration scoped to the shipped variant.
- `.github/workflows/release-build.yml` — no change expected; it already uploads `out/*.apk`. Verified, not assumed, during apply.
- `README.md` — install section.
- `AGENTS.md` — record the shipped architecture matrix and that splitting does not touch the preservation contract (required by the existing `agent-guide` "kept current" obligation).

**Not touched**
- No source files under `app/src/` or `engine/src/`. No dependency changes. No new build types.
- The cut engine, the metadata-preservation contract, and the instrumented suites are unaffected: `connectedAndroidTest` builds `debug`, which is not split and is not minified.

**Measured basis for the figures above** — native payload per architecture extracted from the resolved AAR: `arm64-v8a` 41.8 MB, `armeabi-v7a` 77.7 MB, `x86` 47.1 MB, `x86_64` 48.1 MB (214.7 MB total). Non-native content — dex, resources, manifest — is the ~28.3 MB remainder of the published 243 MB asset. Projected artifact sizes are therefore ~70 MB (`arm64-v8a`), ~76 MB (`x86_64`), ~118 MB (`universal`). These are projections; the first release build confirms them.

**Deferred, with its own change**: enabling code shrinking. It is a further ~18 MB per artifact, and it is excluded because R8 renames Java/Kotlin bytecode that `ffmpeg-kit`'s native code reaches back into by exact class name, while every instrumented suite in this repo builds `debug` and would therefore not observe the regression. Solving that verification gap is a precondition, not a follow-up.
