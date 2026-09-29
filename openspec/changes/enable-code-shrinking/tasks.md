## 1. Preconditions

- [ ] 1.1 Confirm `split-release-apks-by-abi` is applied and archived. This change edits the same `buildTypes.release` block and consumes that change's recorded per-artifact sizes — do not run the two concurrently.
- [ ] 1.2 Re-measure the three artifact sizes locally at the current commit, rather than reusing a figure from an earlier commit, so the before/after pair is same-commit comparable.
- [ ] 1.3 Re-run the reflective-access grep (`Class.forName`, `getIdentifier`, `kotlin.reflect`) across both modules and record it as clean or not. Adding no broad keep rules rests on this being clean.
- [ ] 1.4 Record the current pass state of `./gradlew :app:connectedDebugAndroidTest` and `:engine:connectedAndroidTest` as the reference for group 3.

## 2. Verification harness — shrinking still off

Nothing in this group may change the shipped artifact. If a size moves, something here is wrong.

- [ ] 2.1 Add explicit `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))` while leaving `isMinifyEnabled = false`. Confirm release sizes are unchanged from 1.2.
- [ ] 2.2 Create `app/proguard-rules.pro` with `-keep class com.antonkarpenko.ffmpegkit.** { *; }`, and a comment carrying the evidence: the two native-reachable class names, the 14 extracted `Java_*` symbols, the absence of any `proguard.txt` in the AAR, and the fact that a binary scan finds literal names only — so these rules are a floor, not a ceiling.
- [ ] 2.3 Make release debuggability opt-in via a Gradle property, absent by default. Confirm the default release build is byte-for-byte the same configuration as before.
- [ ] 2.4 Give the debuggable variant a distinct output filename, so a verification build cannot be collected as a release artifact.
- [ ] 2.5 Run `./gradlew :app:connectedReleaseAndroidTest` **without** the property and confirm it fails because the target is not debuggable — not for any other reason. This is the empirical case for the harness existing at all; if it passes, stop and re-read this group.
- [ ] 2.6 Run the same task **with** the property and confirm the suites install and execute. Shrinking is still off, so this proves plumbing only, not safety.

## 3. Enable shrinking, run, and measure

- [ ] 3.1 Set `isMinifyEnabled = true`.
- [ ] 3.2 Run `./gradlew :app:connectedReleaseAndroidTest` with the verification property. Record pass or fail against the 1.4 reference.
- [ ] 3.3 Install the shrunk build on the emulator and complete a cut end to end. Confirm explicitly that no native linkage failure occurs — a green suite is not the same claim as the cut path having run.
- [ ] 3.4 Compare tag survival for the same source video cut by a shrunk and an unshrunk build of this commit. Any tag surviving only unshrunk is a defect to fix or a reason to stop, not a tolerated difference.
- [ ] 3.5 If 3.3 reveals a linkage failure that 3.2 did not, widen the keep rules, re-run 3.2–3.4, and record what was added and which call needed it.
- [ ] 3.6 If a native linkage failure surfaces only at 3.3, record that the instrumented suites do not cover the cut path — that finding is worth more than this change's outcome, and belongs in `AGENTS.md` either way.
- [ ] 3.7 Record per-artifact sizes, shrunk against unshrunk, same commit. All three artifacts, not just the headline one.

## 4. Decide at the threshold

- [ ] 4.1 Apply the gate from `design.md`: is the measured `arm64-v8a` saving ≥ 10 MB, **and** are 3.2–3.4 fully green?
- [ ] 4.2 If yes — leave shrinking enabled and continue to group 5.
- [ ] 4.3 If no — set `isMinifyEnabled = false`, keep the harness and keep rules from group 2, and record the measurement as the reason here. That is this change succeeding at its actual job, not failing. Then skip to group 6.

## 5. Diagnostics and release surface

Runs only if shrinking is staying on.

- [ ] 5.1 Confirm a mapping file is produced for the shrunk release build and note its path.
- [ ] 5.2 Add a release-pipeline step so the mapping accompanies the published release, and confirm a release published without it is treated as incomplete.
- [ ] 5.3 Deobfuscate a synthetic stack trace against the produced mapping and confirm it resolves to source names. A mapping nobody has successfully used is not evidence of retrievability.

## 6. Document

- [ ] 6.1 `AGENTS.md`: the release shrinking state, the verification command, and the fact that `connectedDebugAndroidTest` alone no longer describes the shipped configuration. If group 4.3 was taken, record the measurement as the reason it is off, so nobody re-enables it from enthusiasm.
- [ ] 6.2 `AGENTS.md`: re-extract the `Java_*` symbols from `libffmpegkit.so` and `libffmpegkit_abidetect.so` on any ffmpeg-kit version bump. A new class prefix in a new version is a keep-rule question, and invisible to whoever does the bump.
- [ ] 6.3 Record the measured figures in this change's directory, so the next size discussion cites a number instead of re-estimating one.

## 7. Confirm end to end

- [ ] 7.1 Tag a release candidate. Confirm the published artifacts are shrunk, are **not** debuggable, and carry the mapping.
- [ ] 7.2 Cut a video on a physical device with the published artifact and confirm the output carries GPS location and `creation_time`. `AGENTS.md` already requires a real-device cut before release; a shrunk release raises the stakes on that step, since no debug build exercised this configuration.
- [ ] 7.3 Record the final published asset sizes against the 1.2 figures.
