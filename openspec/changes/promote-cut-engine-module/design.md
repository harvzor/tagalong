## Context

See proposal.md — Why. The proven engine and its contract test currently sit in `cutdebug/src/androidTest/`; `cutdebug/src/main/` is a bare manifest. The behavioral contract is already captured in `openspec/specs/metadata-preserving-cut/spec.md` and is not changing.

Constraints that shape the approach:

- **ffmpeg-kit is a native library.** There is no JVM/Robolectric path; every engine or metadata test must run as an instrumented test (`connectedAndroidTest`) on a booted emulator. This is already true today.
- **Toolchain is fixed** by the existing repo: Gradle 9.5, AGP 9.2.1, Kotlin 2.3.20 via AGP's built-in Kotlin. Applying `org.jetbrains.kotlin.android` or a `kotlinOptions {}` block is a hard build error under AGP 9+.
- **The known re-encode rotation-signal gap** (notes/rotation-reencode-gap.md, carried into `FfmpegCutEngine`'s doc comment) is copied as-is and not fixed here.

## Goals / Non-Goals

**Goals:**
- Stand up `:engine` as a shippable `com.android.library` whose `src/main` holds the proven engine.
- Have the copied contract test run against the production engine in `engine/src/androidTest` and pass exactly as it does today (lossless green; re-encode fails on the known rotation gap).
- Leave `cutdebug` byte-for-byte untouched.

**Non-Goals:**
- No `:app` module, UI, file picker, or SAF Uri↔path bridge (later steps).
- No resolution of the ffmpeg-kit GPL-vs-LGPL licensing question (flagged, deferred — see Open Questions).
- No fix for the re-encode rotation-signal gap.
- No deletion of `cutdebug` or its Media3 arm.

## Decisions

**D1 — Copy, not move.** The engine and contract test are copied into `:engine`; `cutdebug` is not edited. Alternative (move code out of `cutdebug`) was rejected because it violates the user's decision to freeze `cutdebug` as a reference. The usual "two copies drift" hazard is neutralized precisely because `cutdebug` is frozen — a copy that is never edited cannot drift, and both copies carry their own passing contract test, so neither is silently unverified.

**D2 — Contract test lives with the engine.** The contract test is placed in `engine/src/androidTest`, testing `engine/src/main` — its own module, right next door. Alternative (keep the engine's contract test in `cutdebug`, pointed at `:engine`) was rejected: testing a module from a different module's test sourceset is confusing and couples `:engine`'s correctness to a module we intend to delete.

**D3 — `:engine` is a `com.android.library`, mirroring `cutdebug`'s toolchain.** Same `compileSdk`/`minSdk`/Java 17, same built-in-Kotlin setup, same `packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }`. This is the configuration already proven to build cleanly with ffmpeg-kit + media3 present (notes/toolchain.md), so it carries the least risk.

**D4 — ffmpeg-kit becomes a production `implementation` dependency of `:engine`; media3 is not copied.** The engine's `src/main` imports `com.antonkarpenko.ffmpegkit.*`, so ffmpeg-kit must be a `main` dependency. The Media3 transformer/muxer/effect/common deps stay behind in frozen `cutdebug` — they backed the losing arm and the (not-yet-built) rotation fix, neither of which is in scope here.

**D5 — Package identity `dev.tagalong.engine`.** New namespace for the new module, parallel to `dev.tagalong.cutdebug`. The copied files change only their `package` line (and the test's `engine()` wiring) — no logic edits.

## Risks / Trade-offs

- **Shipping GPL** → The `-full-gpl` variant imposes GPL on anything that links `:engine`. Mitigation: surfaced as an explicit Open Question to resolve before an app ships; does not block this structural change (the module needs *some* ffmpeg-kit build to compile, and the variant swap is a one-line dependency change later).
- **Duplicated engine code across two modules** → Mitigation: `cutdebug` is frozen (D1); duplication is bounded and temporary, ending when `cutdebug` is deleted in a later change.
- **Copied re-encode test fails** → This is expected, not a regression (it is the documented rotation-signal gap). Mitigation: the task list calls out the expected pass/fail shape so a red re-encode case is not mistaken for a broken port.
- **Fixture asset duplication** (`xiaomi-poco-x5.mp4` also in `engine/src/androidTest/assets`) → Accepted; the test must be self-contained and the repo already carries the fixture.

## Migration Plan

Purely additive; nothing existing is edited except `settings.gradle.kts` (one `include`). Rollback = delete the `engine/` directory and revert the `settings.gradle.kts` line. No data, no runtime, no released artifact is affected.

## Open Questions

- **ffmpeg-kit licensing variant** — keep `ffmpeg-kit-full-gpl` (GPL, forces a GPL app) or switch to an LGPL-compatible variant/build before the app ships? Safe to defer: it does not change this change's module structure, approach, or task breakdown — only a later dependency-coordinate swap and a licensing decision for the app. Must be resolved before `:app` is distributed.
