## Context

`app/build.gradle.kts` declares a single `release` build type with `isMinifyEnabled = false` and no `proguardFiles`. Enabling shrinking is a one-line change that the rest of this document is about earning.

Three facts constrain the approach.

**The native↔Java surface is real and unprotected.** Extracted from the shipped binaries:

```
libffmpegkit.so            ──▶  com/antonkarpenko/ffmpegkit/FFmpegKitConfig
                                 14 × Java_com_antonkarpenko_ffmpegkit_FFmpegKitConfig_*
libffmpegkit_abidetect.so  ──▶  com/antonkarpenko/ffmpegkit/AbiDetect
```

The AAR contains no `proguard.txt`, and nothing is documented upstream — so no consumer rule protects these, and R8 renames them by default. A miss is an `UnsatisfiedLinkError` on first cut.

**The whole Java layer is 56 KB** inside a 103 MB AAR. Being generous about keep rules costs nothing measurable; being clever about them is the only way to lose.

**Instrumentation cannot run against a non-debuggable app.** Every existing suite builds `debug`. So the configuration that ships is, today, unreachable by any test — and that is the actual problem this change has to solve before the flag means anything.

## Goals / Non-Goals

**Goals:**
- A verification path where the shrinking under test is the shrinking that ships — structurally, not by discipline.
- Keep rules justified by extracted evidence, with the limits of that evidence stated.
- A decidable gate, so "was this worth it?" has an answer rather than a vibe.
- Shrunk-build failures readable after release.

**Non-Goals:**
- Reducing the 89.9 MB of native libraries. R8 does not touch `.so` files.
- Enabling resource shrinking. See below.
- Introducing a product flavor axis, or any second release pipeline that outlives this change.
- Re-examining the ffmpeg-kit variant choice.

## Decisions

### Make release *debuggability* opt-in, rather than adding a build type

The requirement is to run the existing suites against shrunk code, which needs a debuggable target. Two shapes:

**(a)** a dedicated build type that copies the release shrinking configuration;
**(b)** keep one `release` build type and make `isDebuggable` opt-in via a Gradle property.

Chosen: **(b)**.

(a) is the conventional answer and is what most projects do, but it stores the shrinking configuration in two places. Two places drift — usually by someone updating `release` and not the twin — and the resulting failure mode is precisely the one being defended against: the suites pass, the release breaks, and the green run looks like evidence.

With (b) there is one configuration, and `-P<flag>` decides only whether it is debuggable:

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.findByName("release")
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        isDebuggable = providers.gradleProperty("tagalong.verifyShrunk").isPresent
    }
}
```

and verification becomes `./gradlew :app:connectedReleaseAndroidTest -Ptagalong.verifyShrunk`.

The obvious hazard is a debuggable, release-signed APK being published. Mitigated by giving the debuggable variant a distinct output filename suffix, so it cannot be mistaken for a shipped artifact by a human or by the collection step — plus a release-checklist item. The exact property and suffix names are set during apply; the requirement is the *distinctness*, not the spelling.

`getDefaultProguardFile("proguard-android-optimize.txt")` is passed explicitly rather than relying on an implicit default, so the base ruleset is written down. AGP 9.2.1 ships both `proguard-android.txt` and the `-optimize` variant, and the two differ; confirm at apply time which the default would have been, and keep the explicit one. Both filenames are present in `gradle-api-9.2.1.jar`.

### Keep the whole ffmpeg-kit package, not just the two classes found

The evidence names exactly two classes, so two narrow rules look tempting. Rejected.

A binary scan finds only *literal* class names. JNI also resolves a class from a passed-in object — `GetObjectClass` then `GetMethodID` — leaving no string to find. Static inspection therefore cannot bound the surface, and the two names are a floor, not a ceiling. Against 89.9 MB of native code, keeping a 56 KB package is not a trade-off.

```proguard
-keep class com.antonkarpenko.ffmpegkit.** { *; }
```

AGP's default `proguard-android-optimize.txt` already keeps names of classes with native methods, which covers the `Java_*` direction. It does not cover native code calling *into* Java — which is why the explicit rule stays even though it partially overlaps.

`AbiDetect` may well be unreferenced from Java now that architecture splits exist. Keeping the package covers it anyway; stripping a class a `.so` still expects is a worse outcome than 4 KB of dead code.

### Do not enable resource shrinking

`app/src/main/res` is five densities of launcher icons plus four `values` files, every one referenced from the manifest or from `AppTheme`. There is nothing unused to remove, so the saving is near zero — while the failure mode is removing a resource reached by name. It would add risk to buy nothing. Stated here so it is not "helpfully" enabled later as an oversight.

### Publish the mapping as a release asset

AGP writes `app/build/outputs/mapping/release/mapping.txt`. Publish it with the release rather than leaving it in a build directory that vanishes, because the only moment it is needed is after publication, when a user pastes a stack trace into an issue.

The app is GPL with source available, so the mapping discloses nothing not already public. Alternative considered: attach to the tag or keep it in CI artifacts — rejected as places a person reporting a bug will not find.

### Set the gate threshold now, in the open

Enabling shrinking permanently buys a maintenance burden that never ends: every future dependency that bundles native code needs a keep-rule review, and every future crash report needs a mapping lookup. A stated threshold is what makes that trade decidable instead of rhetorical.

**Proposed threshold: ≥ 10 MB saved on the `arm64-v8a` artifact**, measured against an unshrunk build of the same commit, with the shrunk verification run fully green. Below that, the burden is not bought and the flag stays off — with the measurement kept as the reason.

The projected saving is ~18 MB, so the threshold is set below the expectation but above noise. It is a judgement call, deliberately visible, and the first person to disagree should change it here rather than argue it in a task list.

## Risks / Trade-offs

**Keep rules may still be insufficient** → the primary control is the verification run, which is why the spec forbids accepting rules as proof. The suites exercise Compose, navigation, media3 playback, coroutines, and both engine entry points — a linkage break in the cut path fails loudly, not quietly. Residual: a code path none of the suites reach.

**Verification flag misused as a shipping flag** → distinct output filename for the debuggable variant, and a release-checklist item confirming no published artifact is debuggable.

**A future ffmpeg-kit bump changes the native↔Java surface** → the extracted evidence above is a snapshot of version 2.1.0. Re-extract the symbols as part of any ffmpeg-kit version bump; a new `Java_*` prefix in a new version is a keep-rule question. Record this in `AGENTS.md`, since it is invisible to whoever does the bump.

**A future dependency reintroduces reflective access** → current grep for `Class.forName` / `getIdentifier` / `kotlin.reflect` is clean across both modules, which is why no broad keep rules are added today. Clean today is not clean after a dependency addition; treat a new reflection site as a shrinking review.

**Compose under R8** → routinely shrunk upstream, and the risk sits mostly in stack-trace legibility rather than behaviour. The verification run is the check.

**Build time** → shrinking adds a pass to every release build. Docker layer caching does not help, since it is the final step. Tolerable at one release per tag.

**The gate may fail** → then this change's deliverable is the harness and the keep rules, shrinking stays off, and the measurement stands as the reason. That is a success condition, not a partial completion, and the task list is written so it can end there honestly.

## Migration Plan

Three steps, each safe alone, ending at a decision point rather than a committed outcome:

1. **Harness and keep rules only.** Shrinking still off. This lands the ability to run the suites against shrunk code, and changes nothing shipped.
2. **Measure.** Shrunk release build; record per-artifact sizes against an unshrunk baseline of the same commit; run the shrunk verification suites.
3. **Decide** against the threshold. Enable, or leave off with the measurement recorded.

Rollback is `isMinifyEnabled = false`. Nothing under `src/` changes, so there is no code coupling to unwind.
