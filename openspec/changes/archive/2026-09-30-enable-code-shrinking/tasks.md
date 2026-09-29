# Tasks — enable-code-shrinking

> **CLOSED — declining, by owner decision.** `isMinifyEnabled` stays `false`.
> The 24.03 MB/artifact saving was measured and is real (clears this change's own
> 10 MB bar by 2.4x); it was declined because the shrunk build cannot be verified
> automatically (§3.2) and the permanent maintenance burden is not worth it.
> The `E2eCutTest` fix was kept — it is an independent, genuinely dangerous test
> bug, not shrinking work. Full record:
> `notes/apply-measurements.md` (see the DECISION section).
>
> **Do not run `openspec archive` on this change** — its delta spec describes a
> shrunk release this project does not ship, and archiving would merge that
> guarantee into the live specs.

## 1. Preconditions

- [x] 1.1 Confirm `split-release-apks-by-abi` is applied and archived. This change edits the same `buildTypes.release` block and consumes that change's recorded per-artifact sizes — do not run the two concurrently.
- [x] 1.2 Re-measure the three artifact sizes locally at the current commit, rather than reusing a figure from an earlier commit, so the before/after pair is same-commit comparable.
- [x] 1.3 Re-run the reflective-access grep (`Class.forName`, `getIdentifier`, `kotlin.reflect`) across both modules and record it as clean or not. Adding no broad keep rules rests on this being clean.
- [x] 1.4 Record the current pass state of `./gradlew :app:connectedDebugAndroidTest` and `:engine:connectedAndroidTest` as the reference for group 3.

## 2. Verification harness — shrinking still off

Nothing in this group may change the shipped artifact. If a size moves, something here is wrong.

- [x] 2.1 Add explicit `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))` while leaving `isMinifyEnabled = false`. Confirm release sizes are unchanged from 1.2.
- [x] 2.2 Create `app/proguard-rules.pro` with `-keep class com.antonkarpenko.ffmpegkit.** { *; }`, and a comment carrying the evidence: the two native-reachable class names, the 14 extracted `Java_*` symbols, the absence of any `proguard.txt` in the AAR, and the fact that a binary scan finds literal names only — so these rules are a floor, not a ceiling.
- [x] 2.3 Make release debuggability opt-in via a Gradle property, absent by default. Confirm the default release build is byte-for-byte the same configuration as before. **Built and verified, then reverted** — the premise needing it was falsified by 2.5.
- [x] 2.4 Give the debuggable variant a distinct output filename, so a verification build cannot be collected as a release artifact. **Built and verified, then reverted** — it existed only to contain the debuggable flip removed under 2.5.
- [x] 2.5 Run `./gradlew :app:connectedReleaseAndroidTest` **without** the property and confirm it fails because the target is not debuggable — not for any other reason. This is the empirical case for the harness existing at all; if it passes, stop and re-read this group. **STOPPED — premise falsified.** Two findings: (a) a non-debuggable, release-signed target IS instrumentable from an unprivileged (`uid=2000`) shell when the test APK shares its signature — verified green on API 37 with `run-as` still refusing, so `FLAG_DEBUGGABLE` is not the gate; (b) the task does not exist by default at all, because AGP builds an instrumented-test variant for exactly one `testBuildType`. See `notes/apply-measurements.md` §2.5. Needs a design decision before 2.6/3.x proceed.
- [x] 2.6 Run the same task **with** the property and confirm the suites install and execute. Shrinking is still off, so this proves plumbing only, not safety. **GREEN 5/5** after the `E2eCutTest` fix. Confirmed against a target verified non-debuggable (`aapt2`: no `application-debuggable`), with stock AGP output filenames -- i.e. the suites now run against the byte-for-byte shipped configuration.

## 3. Enable shrinking, run, and measure

- [x] 3.1 Set `isMinifyEnabled = true`. **Done, then reverted to `false`.** Shrinking must not stay enabled while 3.2 is not green (4.1's own precondition). Default release re-confirmed byte-identical to the 1.2 baseline.
- [ ] 3.2 Run `./gradlew :app:connectedReleaseAndroidTest` with the verification property. Record pass or fail against the 1.4 reference. **BLOCKED -- build fails, not a test failure.** `testBuildType = "release"` makes AGP also minify the androidTest APK, which fails on `javax.lang.model.element.Modifier` (via espresso-core -> error_prone_annotations). AGP's own remedy is `-dontwarn`, but a test variant has no `proguardFiles` hook and the release ruleset is provably not an input to it. Do NOT read an earlier 5/5 here as green -- it ran a stale unminified test APK against a shrunk app. See notes.
- [ ] 3.3 Install the shrunk build on the emulator and complete a cut end to end. Confirm explicitly that no native linkage failure occurs — a green suite is not the same claim as the cut path having run.
- [ ] 3.4 Compare tag survival for the same source video cut by a shrunk and an unshrunk build of this commit. Any tag surviving only unshrunk is a defect to fix or a reason to stop, not a tolerated difference.
- [ ] 3.5 If 3.3 reveals a linkage failure that 3.2 did not, widen the keep rules, re-run 3.2–3.4, and record what was added and which call needed it.
- [ ] 3.6 If a native linkage failure surfaces only at 3.3, record that the instrumented suites do not cover the cut path — that finding is worth more than this change's outcome, and belongs in `AGENTS.md` either way.
- [x] 3.7 Record per-artifact sizes, shrunk against unshrunk, same commit. All three artifacts, not just the headline one. **Measured 24.03 MB saved on every artifact** (arm64 73,470,465 -> 48,271,524). Verified the whole saving is dex (27.1 MB -> 3.1 MB) with native payload untouched at 11 files / 41.8 MB — an accidental native drop would have given three different numbers, not one identical delta. Clears the >=10MB size half of 4.1. See `notes/apply-measurements.md` §3.7.

## 4. Decide at the threshold

- [x] 4.1 Apply the gate from `design.md`: is the measured `arm64-v8a` saving ≥ 10 MB, **and** are 3.2–3.4 fully green? **Applied — result is mixed, and the mixed-ness is the whole story.** Size half: **yes, decisively** — 24.03 MB vs the 10 MB bar (2.4×). Green half: **no, and not reachable** — see §3.2, AGP blocks it. So the gate as written never resolves cleanly: it assumed the only obstacle was size and that shrinking was freely verifiable.
- [ ] 4.2 If yes — leave shrinking enabled and continue to group 5. **Not taken** (verification half failed).
- [x] 4.3 If no — set `isMinifyEnabled = false`, keep the harness and keep rules from group 2, and record the measurement as the reason here. That is this change succeeding at its actual job, not failing. **Taken, with one deliberate deviation, and a caveat on the framing.**
  - `isMinifyEnabled = false`: done.
  - Measurement recorded as the reason: done — but note this is **not** the "saving below threshold" case this task and the spec scenario describe. The saving is 2.4× the threshold. The reason it is off is that it cannot be proven safe automatically, plus the permanent maintenance burden. Recording it as "not worth it on size" would be a lie that mis-arms the next person.
  - "keep the harness and keep rules": **deliberately NOT done.** With R8 off they are dead config that reads as live — the exact hazard this change argued against elsewhere. All of it was reverted; the keep-rule *evidence* is preserved in the notes instead, which is where it stays useful.

## 5. Diagnostics and release surface

Runs only if shrinking is staying on.

- [ ] 5.1 Confirm a mapping file is produced for the shrunk release build and note its path.
- [ ] 5.2 Add a release-pipeline step so the mapping accompanies the published release, and confirm a release published without it is treated as incomplete.
- [ ] 5.3 Deobfuscate a synthetic stack trace against the produced mapping and confirm it resolves to source names. A mapping nobody has successfully used is not evidence of retrievability.

## 6. Document

- [x] 6.1 `AGENTS.md`: the release shrinking state, the verification command, and the fact that `connectedDebugAndroidTest` alone no longer describes the shipped configuration. If group 4.3 was taken, record the measurement as the reason it is off, so nobody re-enables it from enthusiasm. **Intent satisfied, but deliberately NOT in `AGENTS.md`** -- owner rejected adding it there, correctly: `AGENTS.md` loads into every agent session, and a 33-line note about a decision *not* to act is dead weight in a scarce budget. The guard instead sits where it does useful work, at the flag itself (`app/build.gradle.kts`, `isMinifyEnabled`), which is where anyone about to flip it will be looking; the full record is this change's notes. There is also no shrunk-verification command to document, since 3.2 is blocked. If you are reading this wondering whether to add the decision to `AGENTS.md` -- it was considered and declined on purpose.
- [x] 6.2 `AGENTS.md`: re-extract the `Java_*` symbols from `libffmpegkit.so` and `libffmpegkit_abidetect.so` on any ffmpeg-kit version bump. A new class prefix in a new version is a keep-rule question, and invisible to whoever does the bump. **Recorded in the notes** (all 14 symbols + the 4 `AbiDetect` ones + the no-`proguard.txt` finding + the floor-not-ceiling reasoning) with an explicit re-extract-on-bump instruction. Kept in the notes rather than `AGENTS.md` because shrinking is off and the obligation is dormant until it is not.
- [x] 6.3 Record the measured figures in this change's directory, so the next size discussion cites a number instead of re-estimating one. **Done** — `notes/apply-measurements.md` §1.2 and §3.7.

## 7. Confirm end to end

- [ ] 7.1 Tag a release candidate. Confirm the published artifacts are shrunk, are **not** debuggable, and carry the mapping.
- [ ] 7.2 Cut a video on a physical device with the published artifact and confirm the output carries GPS location and `creation_time`. `AGENTS.md` already requires a real-device cut before release; a shrunk release raises the stakes on that step, since no debug build exercised this configuration.
- [ ] 7.3 Record the final published asset sizes against the 1.2 figures.
