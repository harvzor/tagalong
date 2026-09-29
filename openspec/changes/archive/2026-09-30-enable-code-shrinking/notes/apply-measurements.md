# Apply record — enable-code-shrinking

Running record for the implementation pass. Every figure here is measured on
this commit, not carried over from an earlier one. Commit at start of apply:
`86e3a82 Archive split-release-apks-by-abi and merge specs`.

## Group 1 — Preconditions

### 1.1 Dependency change applied and archived
`split-release-apks-by-abi` is applied (`git log`: commits `85e0175`, `a936b83`)
and archived (`openspec/changes/archive/2026-09-29-split-release-apks-by-abi/`).
It is the change that left `isMinifyEnabled = false` in `buildTypes.release` with
a comment pointing at this change. Not run concurrently. **CLEAN.**

### 1.2 Unshrunk baseline sizes — re-measured at this commit
`./gradlew :app:assembleRelease` (unsigned, no `-PappVersionName`, default
filenames), AGP's flat layout under `app/build/outputs/apk/release/`:

| Artifact | Bytes | MB |
|---|---:|---:|
| `app-arm64-v8a-release-unsigned.apk` | 73,470,465 | 70.07 |
| `app-x86_64-release-unsigned.apk`    | 80,103,784 | 76.39 |
| `app-universal-release-unsigned.apk` | 124,013,744 | 118.27 |

These are the **same-commit unshrunk baseline** the gate at 4.1 measures the
shrunk build against. They match the figures recorded in the archived
`split-release-apks-by-abi` change (70 / 76 / 118 MB) — cross-check passes,
but the numbers above are re-measured here, not reused.

### 1.3 Reflective-access grep — record clean/not
`grep -rn 'Class\.forName|getIdentifier|kotlin\.reflect'` across `app/src` +
`engine/src`: the only hit is a **doc comment** in
`app/src/androidTest/.../LauncherIdentityTest.kt` that explains a deliberately
absent `resources.getIdentifier(...)` assertion. No live reflective call in
either module. **CLEAN** — the decision to add no broad keep rules stands.

### 1.4 Reference pass state of the debug suites (for group 3)
Run on emulator `Medium_Phone` / API 37 (`emulator-5554`):

- **`:app:connectedDebugAndroidTest` → GREEN.** `BUILD SUCCESSFUL`, 5 tests, 0 failed.
- **`:engine:connectedAndroidTest` → 6/7 pass, 1 fail.** The single failure is
  `FfmpegCutEngineTest.reencodeCut_preservesAllSourceTagsIdentically` —
  `rotation signal expected:<90> but was:<0>` on both portrait samples. This is
  the **known, documented re-encode rotation gap** (AGENTS.md "Known open gaps",
  "Rotation signal lost in re-encode mode"), a pre-existing blocker on step 2,
  **not** a regression introduced here. The lossless cut path (the shipped
  configuration) is green.

Reference for group 3: a shrunk run must reproduce this exact state —
`:app` green, `:engine` green-except-the-known-rotation-failure. Anything else
is a new signal.

## Group 2 — Verification harness (shrinking still OFF)

Nothing in this group changed the shipped artifact; confirmed below.

### 2.1 Explicit proguardFiles, `isMinifyEnabled` still false
`proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
file("proguard-rules.pro"))` added to `buildTypes.release`. Shrinking is off, so
no R8 pass runs. Re-measured all three artifacts after the change: byte-for-byte
identical to 1.2 (73,470,465 / 80,103,784 / 124,013,744). Sizes unchanged. ✓

Note: AGP 9.2.1 stores its defaults as fragments (`proguard-common.txt`,
`proguard-header.txt`, `proguard-optimizations.txt`) rather than the two
monolithic files the design checked against. `getDefaultProguardFile(...)` still
resolves the two documented names over those fragments; the build config
resolved without error, which is the empirical confirmation.

### 2.2 `app/proguard-rules.pro`
Created. `-keep class com.antonkarpenko.ffmpegkit.** { *; }`, with the full
extracted evidence in the header comment: the 14
`Java_com_antonkarpenko_ffmpegkit_FFmpegKitConfig_*` symbols, the two FindClass
class strings (`FFmpegKitConfig`, `AbiDetect`), the confirmed absence of any
`proguard.txt` in the AAR (only AndroidManifest.xml, classes.jar, jni/, META-INF/,
R.txt), and the explicit statement that a binary scan finds literal names only —
so the rule is a floor, not a ceiling.

### 2.3 Debuggability opt-in, absent by default
`isDebuggable = providers.gradleProperty("tagalong.verifyShrunk").isPresent`.
Default release build (no property) reproduced the 1.2 sizes byte-for-byte (see
2.1): only `isDebuggable` flips on the property; shrinking/signing/proguardFiles
are untouched. One configuration, not two.

### 2.4 Distinct output filename for the debuggable variant
When `-Ptagalong.verifyShrunk` is set, an `androidComponents.onVariants` block
renames each release output to `app-release-VERIFY[-<abi>].apk`. Confirmed: the
VERIFY outputs are named distinctly and `aapt2 dump badging` reports
`application-debuggable` on them. The default (no property) path keeps AGP's
stock names, untouched.

Hardening beyond the name alone (design: "cannot be mistaken ... by the
collection step"): `scripts/collect-apks.sh` now *refuses* to collect any APK
carrying the `-VERIFY` marker, so a stale debuggable build sitting in the
`release/` leaf directory cannot be exported as a release artifact even by the
script. Verified both directions: default tree collects 3 artifacts (exit 0);
tree containing a VERIFY build exits 1 with an explicit message.

### 2.5 BLOCKED — the design's central premise is falsified

2.5 reads: "confirm it fails because the target is not debuggable — **not for
any other reason** ... if it passes, stop and re-read this group." It passed.
Stopping and reporting.

**The design's stated blocker does not hold on this device.** `design.md`
asserts: "Instrumentation cannot run against a non-debuggable app. Every
existing suite builds `debug`. So the configuration that ships is, today,
unreachable by any test." Measured on the README's verified AVD
(`Medium_Phone`, API 37, `ro.build.type=user`, `ro.debuggable=0`), with shrinking
still OFF, a genuinely **non-debuggable** release APK was installed and
instrumented to a green result:

- `aapt2 dump badging` on the target: no `application-debuggable`.
- `dumpsys package`: `pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP
  KILL_AFTER_RESTORE ]` — no `DEBUGGABLE`.
- `run-as dev.tagalong.app` -> `run-as: package not debuggable` (the framework's
  own confirmation the flag is absent).
- `am instrument -w ... LauncherIdentityTest` -> `OK (1 test)`.
- adbd ran as `uid=2000(shell)`, **not root** — so this is not a rooted-device
  bypass.

Instrumentation of a non-debuggable target succeeded from an unprivileged shell.
The gate that actually matters is **signature matching** between the target and
the test APK, not `FLAG_DEBUGGABLE`: with both signed by the same verification
keystore, the framework allowed it. `run-as` still refuses, which is exactly the
debuggable-gated capability, so the two are genuinely distinct surfaces.

**Consequence.** If the shipped release is reachable by the suites as-is, then
making release debuggable is not required to verify the shrunk configuration —
and the whole opt-in switch, the distinct VERIFY filename, and the collect-script
hard-refusal exist to solve a problem this device says does not exist.

**Second, independent finding (AGP mechanics).** `:app:connectedReleaseAndroidTest`
does not exist by default at all. AGP creates an instrumented-test variant for
exactly **one** build type (`android.testBuildType`, default `debug`). The
design never mentions this; it assumed only that debuggability was the gate. So a
second knob had to be wired — `testBuildType = if (verifyShrunkRequested)
"release" else "debug"` — for the verification task to exist. Had the debuggable
premise been true, the documented command would still have failed with
"task not found", i.e. the design's verification recipe was incomplete in a
second way.

### 2.6 Plumbing works; E2eCutTest is flaky (independent of this change)

With `-Ptagalong.verifyShrunk`, the suites install and execute against the
release variant — 4/5 green, `E2eCutTest` red on `StaleObjectException`.

Root-caused as **not shrinking-related and not debuggability-related**: with
shrinking still OFF the bytecode is identical to debug, and the **debug** variant
reproduces the same `StaleObjectException` on a clean emulator reboot
(3 re-runs: fail, fail, pass). `E2eCutTest` drives UIAutomator + Compose across
two samples and is order/state sensitive; it passed green at 1.4 and fails
intermittently since. Treating it as a known-flaky pre-existing condition, not a
signal from this change.

**This matters for the gate at 4.1**, which requires 3.2 green. A
flaky-by-itself test cannot be a green/red discriminator for shrinking. Group 3
needs a stable signal: either the cut path asserted by a non-flaky route (3.3's
manual device cut, which 3.6 already anticipates), or the flake fixed first.

## 3.7 measured early — the size answer, independent of the 2.5 blocker

Task 3.7 (record per-artifact sizes shrunk vs unshrunk) does not depend on the
verification harness working, so it was measured directly at this commit with
`isMinifyEnabled = true` plus the group-2 keep rules. Reverted to `false`
afterwards: 4.1's gate also requires 3.2-3.4 green, which is not yet satisfiable.

### Sizes, same commit, both unsigned default-filename builds

| Artifact | Unshrunk | Shrunk | Saved |
|---|---:|---:|---:|
| `arm64-v8a` | 73,470,465 | 48,271,524 | 25,198,941 (**24.03 MB**) |
| `x86_64` | 80,103,784 | 54,904,843 | 25,198,941 (**24.03 MB**) |
| `universal` | 124,013,744 | 98,814,803 | 25,198,941 (**24.03 MB**) |

The saving is byte-identical across all three artifacts, as expected: the dex is
shared by every ABI split while the native libraries are not. An accidental
native-lib drop would have produced three *different* numbers.

### Where the 24 MB comes from — verified, not assumed

APK entry inspection, `arm64-v8a`:

| | dex files | dex (uncompressed) | native files | native (uncompressed) |
|---|---:|---:|---:|---:|
| unshrunk | 3 | 27.1 MB | 11 | 41.8 MB |
| shrunk | 1 | 3.1 MB | 11 | 41.8 MB |

- **Native payload unchanged** — 11 files, 41.8 MB, identical before and after.
  Nothing was dropped. (41.8 MB also matches the figure the archived
  `split-release-apks-by-abi` change extracted from the AAR for this ABI.)
- **The entire saving is dex**: 27.1 MB -> 3.1 MB, 3 files consolidated to 1.
- Dex is evidently stored near-uncompressed, which is why the uncompressed dex
  delta (~24 MB) and the on-disk APK delta (24.03 MB) match almost exactly.
- Arithmetic sanity check: 41.8 native + 27.1 dex + 0.79 res + ~3.8
  alignment/manifest overhead = 73.5 MB, the measured unshrunk size.

### Against the gate

`design.md` sets the threshold at **>= 10 MB on `arm64-v8a`**. Measured: **24.03
MB** — roughly 2.4x the threshold, and above the ~18 MB the proposal projected.
The size half of the 4.1 gate clears decisively.

### But this is the number that raises the stakes, not lowers them

R8 deleted **88% of the app's bytecode** (27.1 MB -> 3.1 MB) while the native code
that calls back into it by exact class-and-method name stayed byte-identical.
That is precisely the failure surface `app/proguard-rules.pro` was written for,
at maximum aggression.

No cut has been executed on a shrunk build yet (3.3). Per the spec's own
"Keep rules are not accepted as proof" scenario, a 24 MB saving with an
unexercised cut path is **not** a green result. The big number strengthens the
case for fixing the verification path properly (and the `E2eCutTest` flake,
which is the one test that actually drives a cut through the UI) rather than for
skipping it.

Note: 41.8 MB of native code is now ~87% of the shrunk artifact. Shrinking the
dex further has very little left to give; the remaining lever is the ffmpeg-kit
variant, which the proposal lists as out of scope.

## Resolving 2.5 - the switch, rebuilt (approved by owner)

The debuggable flip was removed, because it was only ever needed by the falsified
premise. Two pieces of machinery went with it, and one was genuinely required:

| Mechanism | Verdict |
|---|---|
| `isDebuggable = verifyShrunkRequested` | **Removed.** Not required for instrumentation. |
| `-VERIFY` output filename (`androidComponents.onVariants` + `outputFileName`) | **Removed.** Its only job was to contain a debuggable, release-signed artifact; none is ever produced now. |
| `scripts/collect-apks.sh` hard-refusal of `*-VERIFY*` | **Removed.** Fully reverted - `git diff` on that file is now empty. |
| `testBuildType = if (verifyShrunkRequested) "release" else "debug"` | **Kept.** This is the actual gate on the task existing. |

The anti-drift property the design cared about is untouched: still exactly one
`release` build type, still one switch, so the shrinking config cannot be stored
in two places.

**Verified with shrinking still OFF** - full 5-test `:app` suite green via
`:app:connectedReleaseAndroidTest -Ptagalong.verifyShrunk`, against a target
confirmed non-debuggable (`aapt2` finds no `application-debuggable`), with stock
AGP output filenames. The suites now exercise the byte-for-byte shipped
configuration rather than a slightly-opened variant of it.

## Flake fixed at root cause (was blocking 3.2 / 4.1)

`E2eCutTest` was the only test that drives a real cut, so it had to become a
trustworthy signal before the 4.1 gate could mean anything.

**Reporting defect first:** failures were recorded as
`failure.message ?: simpleName`, so a message-less exception collapsed to a bare
class name and the stack was discarded. That is why the failure had read as
un-diagnosable. `Throwable.describe()` now keeps class, message, the
`dev.tagalong.*` / `compose.ui.test` frames, and the cause chain.

**Root cause:** `FilePickerRobot.selectItem` line 79,
`exactMatches.single().click()`. `UiObject2` is a handle onto an accessibility
node, not a durable locator. The handles are collected at line 76
(`findObjects`) and DocumentsUI rebinds/animates its result rows immediately
after the search string is applied - exactly the interval before the click - so
the node is recycled and the click throws `StaleObjectException`. Consistent with
the observed pattern of failing on the *first* sample, and with intermittency.

**Fix:** `clickCurrentMatch` re-resolves the selector immediately before each
click and retries on `StaleObjectException`. The exact-vs-stem match counting
that decides *what* to click is unchanged, so ambiguity is still reported rather
than retried away.

**Cascade contained:** `dismissIfOpen` pressed BACK once. With the search field
focused, one BACK closes the keyboard or exits search while DocumentsUI stays
foreground; with the picker left up, no Compose hierarchy is reachable, so one
sample's failure made every later sample fail with an unrelated-looking "No
compose hierarchies found". Now a bounded loop until the app is foreground.

**Measured:** 5/5 consecutive green (previously fail/fail/pass in the same
conditions); full 5-test suite green.

## 3.2 BLOCKED - new finding: AGP minifies the test APK with no way to give it rules

With `isMinifyEnabled = true`, `testBuildType = "release"` makes AGP derive the
androidTest variant from `release` as well, so it runs
`:app:minifyReleaseAndroidTestWithR8` - something that never happens for debug
test builds. It fails:

```
ERROR: R8: Missing class javax.lang.model.element.Modifier
  (referenced from: ... com.google.errorprone.annotations.IncompatibleModifiers.value())
```

Owner: `androidx.test.espresso:espresso-core:3.7.0` ->
`error_prone_annotations:2.30.0`. An ordinary, expected test dependency; the
annotation is CLASS-retention and never loaded, which is why AGP itself offers
`-dontwarn javax.lang.model.element.Modifier` in
`app/build/outputs/mapping/releaseAndroidTest/missing_rules.txt`.

**The trap:** that rule cannot be applied. `-dontwarn` added to
`app/proguard-rules.pro` has no effect on the test variant - verified by adding
it and re-running (identical failure, task not up-to-date), and by inspecting the
task inputs: `releaseAndroidTest` sees only
`default_proguard_files/global/proguard-android{,-optimize}.txt-9.2.1` plus its
own `aapt_rules.txt`. AGP exposes no `proguardFiles` for a test variant, and
`TestVariantBuilder.isMinifyEnabled` (`CanMinifyCodeBuilder`) is not reachable
from `ApplicationAndroidComponentsExtension`, whose only hooks are
`beforeVariants`/`onVariants` typed to the *main* variant builder.

So: an anticipated, ordinary test dependency makes the verification build fail,
and the one documented remedy is unreachable through supported API.

**Evidence-integrity note.** The first 3.2 run appeared to pass 5/5 while the
build failed. It must not be read as green: the test-APK minification failed, so
those tests ran against a *stale* unminified test APK from the previous
(shrinking-off) build, paired with a freshly shrunk app - a mixed artifact. 3.2
requires a clean run of both APKs; re-running with `--rerun-tasks` and cleared
`outputs/apk` reproduces the R8 failure instead.

`isMinifyEnabled` was reverted to `false` accordingly - the 4.1 gate needs 3.2
green, which is not currently reachable. Default release re-confirmed
byte-identical to the 1.2 baseline (73,470,465) and the debug suite is green.

### Options for 3.2

1. **Ship-time shrink, verify by execution outside the suites** - rely on 3.3's
   manual device cut as the real check. Honest, but concedes the spec's
   "executed by the automated suites" requirement is not met.
2. **Force the rule in via an unsupported route** (e.g. a task input tweak on
   `minifyReleaseAndroidTestWithR8`). Fragile across AGP upgrades, and silently
   reverts to broken.
3. **Exclude `error_prone_annotations` from the androidTest classpath.** R8 then
   reports the *annotation* as missing instead - likely the same wall.
4. **Revisit the gate**: keep shrinking off (4.3) with the 24.03 MB measurement
   recorded as the reason, and keep the harness for the debug path. The design
   explicitly sanctions this as success.
5. **Reconfigure the verification build** so the target is shrunk but the test
   variant is not (e.g. shrink only via a distinct artifact rather than via
   `testBuildType`) - needs design work; the two-places-to-drift objection has
   to be answered first.

## DECISION - shrinking declined by owner; change closed here

**Outcome: `isMinifyEnabled` stays `false`.** Declined deliberately, on maintenance
grounds, not on size grounds.

The distinction matters and should not be flattened by a later reader:

- The 4.1 gate's **size** half was met decisively -- 24.03 MB/artifact against a
  10 MB threshold (2.4x).
- The gate's **verification** half was not reachable, for the AGP reason
  recorded in §3.2 above.
- So this is *not* the "saving below threshold -> leave off" path that 4.3 and
  the spec scenario describe. It is "saving is real, but it cannot be proven
  safe automatically, and the permanent burden is not worth 24 MB."

### What was kept

`app/src/androidTest/.../E2eCutTest.kt` and `FilePickerRobot.kt` -- the
`StaleObjectException` root-cause fix and the failure-reporting fix. **Kept
because it is not about shrinking.** `E2eCutTest` is the only test that drives a
real cut, i.e. the app's core correctness claim, and it was intermittently
throwing away its own stack trace while failing non-deterministically on healthy
builds. That is worth fixing under any release plan, and it is verified 5/5 green
plus a green full suite.

### What was reverted (all shrinking-specific, so none of it is needed)

| Change | Reverted? |
|---|---|
| `testBuildType` + `-Ptagalong.verifyShrunk` switch | Reverted. |
| `proguardFiles(...)` on release | Reverted. |
| `app/proguard-rules.pro` | **Deleted** -- inert with R8 off, and dead config that looks live is a hazard. |
| `-VERIFY` output renaming | Reverted (had already been removed). |
| `scripts/collect-apks.sh` refusal | Reverted -- file has zero diff. |
| `.gitignore` entry + local `verification-release.keystore` | Reverted / deleted. |

`isMinifyEnabled = false` carries a rewritten comment recording the decision, so
nobody re-enables it from enthusiasm -- the failure mode 6.1 was written to
prevent.

### Evidence preserved (the ffmpeg-kit native surface)

The rules file is gone, so the extraction that justified it lives here instead.
From `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` -- re-extract on any version
bump, since a new class prefix is a keep-rule question invisible to whoever does
the bump:

- `libffmpegkit.so` -> class `com/antonkarpenko/ffmpegkit/FFmpegKitConfig`, and 14
  exported `Java_com_antonkarpenko_ffmpegkit_FFmpegKitConfig_*` symbols
  (`disableNativeRedirection`, `enableNativeRedirection`, `getNativeBuildDate`,
  `getNativeFFmpegVersion`, `getNativeLogLevel`, `getNativeVersion`,
  `ignoreNativeSignal`, `messagesInTransmit`, `nativeFFmpegCancel`,
  `nativeFFmpegExecute`, `nativeFFprobeExecute`, `registerNewNativeFFmpegPipe`,
  `setNativeEnvironmentVariable`, `setNativeLogLevel`).
- `libffmpegkit_abidetect.so` -> class `com/antonkarpenko/ffmpegkit/AbiDetect`
  and 4 `Java_..._AbiDetect_*` symbols.
- **The AAR ships no `proguard.txt`** -- only `AndroidManifest.xml`, `classes.jar`,
  `jni/`, `META-INF/`, `R.txt`. Nothing protects this surface from renaming.
- `classes.jar` is 56 KB, so keeping the whole package costs nothing measurable.
- A binary scan finds **literal** names only; JNI also resolves classes from
  passed-in objects (`GetObjectClass` -> `GetMethodID`), leaving no string to
  grep. So these two class names are a **floor, not a ceiling** -- the reason the
  correct rule is the whole package, `-keep class com.antonkarpenko.ffmpegkit.** { *; }`,
  rather than two narrow lines.
- Reflective access elsewhere is clean: the only repo hit for
  `Class.forName|getIdentifier|kotlin.reflect` is a doc comment in
  `LauncherIdentityTest.kt`.

### Do NOT archive this change with `openspec archive`

Its delta spec (`specs/minified-release/spec.md`) describes behaviour this project
does **not** have -- a shrunk release verified by execution. Archiving would merge
that into the live specs, creating a contract claiming a guarantee that is not
implemented and, per §3.2, currently cannot be. Leave it here, closed and
declined, as the record of a measured decision.

### If this is ever reopened

Two things, in order: solve the missing-rule problem for the test variant (the one
untried avenue is routing `-dontwarn` through a mechanism that *does* reach test
variants, e.g. consumer rules from a module), then treat the real-device cut that
`AGENTS.md` already mandates before release as the actual gate rather than trying
to replace it. Worth noting: that checklist already performs the exact gesture --
cutting a video on a physical device -- that would surface an R8 rename
regression.

