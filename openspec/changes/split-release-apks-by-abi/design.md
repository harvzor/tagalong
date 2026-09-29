## Context

`app/build.gradle.kts` currently declares no `splits` and no `ndk { abiFilters }`, so the release APK carries all four architectures bundled by `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0`. AGP stores native libraries uncompressed, so the published asset is a straight sum: 214.7 MB of `.so` across four `jni/` folders plus ~28.3 MB of dex and resources = the observed 243 MB.

Two constraints shape the approach:

**Distribution is GitHub Releases, not a store.** An Android App Bundle would solve this for free — a store slices it per device — but a `.aab` is not installable by tapping a file, and GitHub is not a store. So the release must contain finished APKs, and the reduction has to come from what goes inside them. This is also why the change is expected to be temporary in shape: if Play ever ships, the artifact set is replaced by a single AAB and this design retires.

**The packaging step assumes exactly one output.** `Dockerfile:89-95`:

```dockerfile
find app/build/outputs/apk -name "*.apk" | while read f; do \
    if [ -n "${VERSION}" ]; then \
        cp "$f" "/out/tagalong-${VERSION}.apk"; \
```

One input, one destination — correct today, destructive with multiple inputs. This is the only genuine defect in the change, and it is the only one that fails silently.

## Goals / Non-Goals

**Goals:**
- Design-level boundaries beyond the proposal: the exact Gradle configuration shape, the artifact naming scheme, and the version-code policy across artifacts.
- Establish which properties must be *measured on a real build* rather than reasoned about, before anything is published.

**Non-Goals:**
- Reducing the 89.9 MB of native payload that remains. That is the `full-gpl` variant question — it requires re-verifying the whole metadata-preservation matrix and collides with the open re-encode rotation gap.
- Code shrinking. See *Deferring minification* below.
- Introducing an `.aab` publish path, or Play Store preparation.
- Changing anything under `app/src/` or `engine/src/`.

## Decisions

### Use `splits.abi` with an explicit `include`, not `ndk.abiFilters`

`ndk.abiFilters` restricts which architectures go into a single APK. That alone would produce the 118 MB combined artifact and nothing else — one file, no per-device option, and it discards the benefit of publishing per-architecture builds.

`splits.abi` produces per-architecture APKs *and*, with `universalApk = true`, the combined one. Note that `include` governs both: the `universal` artifact is built from the same list, never broader than it. So dropping a 32-bit architecture from `include` removes it from the combined artifact too — the combined artifact is a convenience, not a compatibility guarantee beyond the shipped set.

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "x86_64")
        isUniversalApk = true
    }
}
```

`reset()` matters: without it the default ABI list is retained alongside the explicit one.

### Ship the `universal` artifact

Two reasons, one of them a hedge against this document's own weak point (see *Risks* — the 32-bit population claim is asserted, not verified). The other is that a decision with three options and no default is a support cost disproportionate to the 6 MB spread between the two remaining architectures.

With 32-bit dropped, the combined artifact is ~118 MB rather than the 243 MB it would have been. That is what makes it a defensible default rather than a re-run of today's problem.

### Identical `versionCode` on all three artifacts

LibreCuts offsets `versionCode` per architecture (`+0`/`+1`/`+2`). Not adopted.

Unique codes matter to a store that resolves updates by `versionCode` across multiple uploads. GitHub Releases resolves nothing, so the offset buys nothing here and costs something specific: a user who switches artifact on the same device — the recovery path this change itself documents — would be offered a *lower* `versionCode` and Android would report a downgrade rather than switching architecture.

This does not foreclose Play. Play takes one `versionCode` per AAB and the ABI splits derived from it share it, so the policy is the same either way.

### Human-facing names use hyphens; derived from the build, not enumerated

AGP emits architecture identifiers with underscores (`arm64_v8a`). Release filenames use the canonical Android spelling instead — an explicit mapping: `arm64_v8a` → `arm64-v8a`, `x86_64` → `x86_64`, the combined artifact → `universal`.

The mapping is explicit because a blanket underscore-to-hyphen substitution is wrong here: it leaves `arm64-v8a` correct and mangles `x86_64` into `x86-64`, which is not an architecture name. The canonical spellings are also what a reader sees in device settings and what every comparable project publishes.

The collection step derives each identifier from the output it is holding and does not hard-code a count or a fixed list. A future `include` change then needs no matching edit in `Dockerfile`, and an unexpected output count is not silently truncated.

### The workflow needs no change, verified rather than assumed

`.github/workflows/release-build.yml` already uploads `out/*.apk`. It should be confirmed to pass three distinct assets on the first tagged build rather than trusted on inspection.

### Deferring minification is a sequencing decision, not a rejection

`isMinifyEnabled` stays `false`, and this is deliberate rather than an oversight, so it is recorded where a future reader will not "helpfully" flip it.

R8 shrinks Java/Kotlin bytecode only; it cannot touch the 89.9 MB of prebuilt `.so` files, which is what the release actually consists of. Its whole prize is the ~28.3 MB remainder, of which the resource shrinker addresses almost none — `res/` is five densities of launcher icons, every one referenced from the manifest. Realistic prize: ~18 MB per artifact.

The cost is not the two keep rules. `ffmpeg-kit` ships no consumer ProGuard rules at all, and its native code reaches Java by exact name — extracted from the shipped binaries, the surface is `com.antonkarpenko.ffmpegkit.FFmpegKitConfig` (14 `Java_com_antonkarpenko_ffmpegkit_FFmpegKitConfig_*` symbols) and `com.antonkarpenko.ffmpegkit.AbiDetect`. Keeping the package costs ~56 KB against 89.9 MB, so a blunt keep rule is correct and cheap.

The cost is that a rename regression surfaces as `UnsatisfiedLinkError` the first time a user taps Cut, in a release build, and **every instrumented suite in this repo builds `debug`, where minification is off** — so no existing test would observe it. Solving that coverage gap is a precondition for enabling the flag, not a follow-up to it. Keeping the whole package is still necessary but not sufficient: a JNI name resolved from a passed-in object leaves no string in the binary to find, so static inspection cannot bound the surface.

## Risks / Trade-offs

**ABI splits apply to every build type, including `debug`** → not requested, and unverified for its effect on `connectedAndroidTest` install behaviour, since the app-under-test and the test APK each become architecture-scoped. Verify early, before publication: `./gradlew :app:connectedDebugAndroidTest` and `:engine:connectedAndroidTest` on the `arm64-v8a` emulator. `:engine` is a library module and does not apply `splits`, so its androidTest APK is expected to be unaffected — expected, not assumed. Fallback if the debug loop regresses: restrict the split configuration to release variants and re-run. This must be settled by execution, not argument.

**Output directory layout under `splits.abi` is not known in advance** → AGP may emit to `apk/<abi>/release/` or flat under `apk/release/`. Do not encode a guessed path. On the first build, list the actual outputs and derive the collection pattern from what is really there. The existing recursive `find` happens to also reach `apk/androidTest/debug/`, which is currently unreachable only because `assembleRelease` never builds test outputs — a coincidence, not a guard.

**Dropping 32-bit rests on an unverified claim** → `minSdk` 31 means Android 12 (2021) and the local toolchain holds only `arm64-v8a` images, but no Android 12+ device being 32-bit-ARM-only could not be confirmed from documentation. Note that the `universal` artifact does *not* mitigate this, being built from the same `include` list. Mitigation actually available: the failure is an install-time `INSTALL_FAILED_NO_MATCHING_ABIS` rejection, never a broken install that fails later, so the worst case is a report from a user on old hardware with an accurate error. Revisit if such an issue is filed — restoring an architecture is an `include` edit.

**Total uploaded payload rises** → three artifacts at ~70/~76/~118 MB is ~264 MB per release, against today's single 243 MB. Downloads get smaller; the release folder gets bigger. Accepted.

**Existing download automation breaks** → anything scripted against `tagalong-<version>.apk` stops resolving. Covered by the filename change being called BREAKING in the proposal and by the README install section.

**Users upgrading from an existing 243 MB install** → same package id and signature, so a per-architecture artifact should replace it. Not relied upon: the install section documents uninstall-before-switch as the recovery path, and the first split release's notes should repeat it.

## Migration Plan

Land in two steps, first one safe in isolation:

1. **The packaging fix on its own.** Per-architecture destination naming and variant-scoped enumeration are behaviour-preserving when the build produces exactly one APK. Merging it first removes the silent-overwrite hazard from any build that ever emits more than one output, independently of the rest.
2. **The split configuration**, then a tagged release to confirm three signed assets.

Rollback is a build-config revert; nothing under `src/` changes, so there is no data or migration coupling. If a published release needs to be pulled, retagging is sufficient — `app/build.gradle.kts` derives `versionCode` from `-PappVersionName` and does not change.
