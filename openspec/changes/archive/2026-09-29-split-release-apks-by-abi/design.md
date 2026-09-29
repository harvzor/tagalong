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

### Use `splits.abi` for the split, `ndk.abiFilters` for the bound — one list feeds both

_This decision was corrected during apply. An earlier version asserted that `include` alone
bound the combined artifact. Measurement disproved it; see_ _*Universal merges every ABI the
`include` list does not bound it`_* _below._

Two knobs do two different jobs, and both are required:

- **`splits.abi.include(...)`** decides **which per-architecture APKs get generated**.
- **`ndk.abiFilters`** bounds **which architectures enter the merged native-lib set**, and
  therefore what the `universal` artifact can contain.

`include` on its own is not sufficient. Measured on AGP 9.5 with only `include("arm64-v8a",
"x86_64")`, the combined artifact shipped all four ABIs the AAR bundles — 243 MB, containing
`armeabi-v7a` and `x86`, i.e. the 32-bit payload this change exists to remove. The two per-ABI
splits were correct; the combined artifact silently misrepresented itself.

Using `ndk.abiFilters` alone would be the opposite failure: one combined APK, no per-device
option, discarding the point of splitting at all.

Because two lists that must agree are a drift hazard — the exact "silent misrepresentation"
failure mode just measured — the architecture set is declared **once** and consumed by both:

```kotlin
// Single source of truth for the shipped architecture set.
val shippedAbis = listOf("arm64-v8a", "x86_64")

defaultConfig {
    ndk {
        abiFilters += shippedAbis          // bounds merged native libs -> bounds the universal APK
    }
}

splits {
    abi {
        isEnable = true
        reset()
        include(*shippedAbis.toTypedArray())   // decides which per-architecture splits are generated
        isUniversalApk = true
    }
}
```

`reset()` still matters: without it the AGP default ABI list is retained alongside the explicit one.

Measured with both knobs fed from one list, the combined artifact is 118 MB and contains exactly
`arm64-v8a` and `x86_64` — the `release-artifacts` requirement that the combined artifact be
exactly the sum of the shipped set, never more permissive than it.

Dropping an architecture from `shippedAbis` therefore removes it from the splits *and* from the
combined artifact in one edit. The combined artifact remains a convenience, not a compatibility
guarantee beyond the shipped set.

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

**Output directory layout under `splits.abi` is not known in advance** → resolved by measurement, not prediction: AGP 9.5 emits **flat** under `apk/<variant>/`, not nested per-ABI. Sizes: `arm64-v8a` 70 MB, `x86_64` 76 MB, `universal` 118 MB — the first two matched the proposal's projection exactly. The collection step still searches for a *leaf directory* named after the variant, so it works under either layout rather than encoding the observed one.

**AGP's release filename spelling is not what was assumed** → the export step anticipated the underscore form (`arm64_v8a`) and mapped it to the canonical `arm64-v8a`. AGP 9.5 already emits the canonical hyphenated form in the filename. The explicit mapping is kept regardless: it is a no-op for the spelling observed and still correct if another AGP reverts to the underscore form. What must not happen is a blanket underscore-to-hyphen rewrite, which would emit the non-existent `x86-64`.

**Universal merges every ABI the `include` list does not bound it** → the design-level finding of this change, and the one that would have shipped silently. Every per-split artifact was correct, so nothing in a build log or an install test would have flagged it; only opening the combined APK and listing its `lib/` directory did. Two artifacts can therefore disagree about what the app supports, with the one users are told to download being the wrong one. Caught by task 5.3/5.4, which mandated inspection over assumption. Fixed by feeding `ndk.abiFilters` and `splits.abi.include` from one list, above. Standing lesson: **the combined artifact's contents are an assertion the build never verifies** — inspect `lib/` in any future change that touches the architecture set.

**Dropping 32-bit rests on an unverified claim** → `minSdk` 31 means Android 12 (2021) and the local toolchain holds only `arm64-v8a` images, but no Android 12+ device being 32-bit-ARM-only could not be confirmed from documentation. Note that the `universal` artifact does *not* mitigate this, being built from the same `include` list. Mitigation actually available: the failure is an install-time `INSTALL_FAILED_NO_MATCHING_ABIS` rejection, never a broken install that fails later, so the worst case is a report from a user on old hardware with an accurate error. Revisit if such an issue is filed — restoring an architecture is an `include` edit.

**Total uploaded payload rises** → three artifacts at ~70/~76/~118 MB is ~264 MB per release, against today's single 243 MB. Downloads get smaller; the release folder gets bigger. Accepted.

**Existing download automation breaks** → anything scripted against `tagalong-<version>.apk` stops resolving. Covered by the filename change being called BREAKING in the proposal and by the README install section.

**Users upgrading from an existing 243 MB install** → same package id and signature, so a per-architecture artifact should replace it. Not relied upon: the install section documents uninstall-before-switch as the recovery path, and the first split release's notes should repeat it.

## Migration Plan

_During apply:_ the packaging fix and its verification ran against the **host** build tree via
`scripts/collect-apks.sh`, not inside a container. Extracting the collection step into that script
made it a single source of truth that the Dockerfile merely invokes, so the selection, naming and
collision logic is exercisable directly — including the planted-second-APK and androidTest-present
cases — without a full container build per iteration. No `docker` binary exists on this host (only
`podman`, which has never built this image); the container build itself is exercised authoritatively
by CI on native Docker, where task 7.2 confirms the published asset set.

_During apply:_ the debug-loop risk resolved clean. `splits.abi` also applies to `debug`, so
`debug` now emits per-ABI APKs too, but Gradle installs the one matching the device and
`:app:connectedDebugAndroidTest` stayed at 5/5 with `:engine:connectedAndroidTest` unchanged from
baseline. Task 4.2's release-only restriction was therefore **not** applied — recorded as evaluated,
not implemented.

Land in two steps, first one safe in isolation:

1. **The packaging fix on its own.** Per-architecture destination naming and variant-scoped enumeration are behaviour-preserving when the build produces exactly one APK. Merging it first removes the silent-overwrite hazard from any build that ever emits more than one output, independently of the rest.
2. **The split configuration**, then a tagged release to confirm three signed assets.

Rollback is a build-config revert; nothing under `src/` changes, so there is no data or migration coupling. If a published release needs to be pulled, retagging is sufficient — `app/build.gradle.kts` derives `versionCode` from `-PappVersionName` and does not change.
