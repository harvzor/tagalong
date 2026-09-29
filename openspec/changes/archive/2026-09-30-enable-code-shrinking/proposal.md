## Why

The release APK is ~90 MB of prebuilt native libraries plus a ~28 MB remainder of Java/Kotlin bytecode and resources. Code shrinking attacks only the remainder, and it is the last cheap lever available before reducing size further means rebuilding ffmpeg-kit itself.

It cannot simply be switched on. `ffmpeg-kit`'s native code reaches back into Java by **exact class and method name**, and the library ships **no consumer ProGuard rules at all** — nothing protects it from renaming. A rename regression surfaces as `UnsatisfiedLinkError` the first time a user cuts a video, in a release build only.

That would be tolerable if anything could catch it. Nothing can: every instrumented suite in this repo builds `debug`, where shrinking is off, so the shipped transformation is the one configuration that has never been executed by a test. **Building that coverage is therefore this change's precondition, not its follow-up.**

## What Changes

- A release build can be run through the existing instrumented suites with shrinking active, so the transformation that ships is a transformation that has been executed. Achieved without introducing a second shrinking configuration that could drift from the shipped one.
- Explicit keep rules for the native↔Java entry points, derived from the shipped binaries rather than guessed.
- `isMinifyEnabled` becomes `true` for release — **gated on a measured saving clearing a stated threshold**, with the gate's numbers recorded. The projected saving is ~18 MB per artifact and is explicitly a projection; a build has never been shrunk here.
- `mapping.txt` for each published release is retained, so a field stack trace stays readable.
- Resource shrinking is deliberately **not** enabled. `res/` is five densities of launcher icons, every one referenced from the manifest — the expected saving is close to zero and the failure mode is removing a resource that is reached by name.

## Capabilities

### New Capabilities

- `minified-release`: the contract for a code-shrunk release build — that shrinking is verifiable by execution rather than assumed, that behaviour is unchanged by it, that native-reachable entry points survive it, and that failures in a shrunk build remain diagnosable.

### Modified Capabilities

None. The core preservation obligations stay in `metadata-preserving-cut`; this capability adds the shrinking-specific guarantee that shrinking does not perturb them, rather than restating them.

## Impact

**Depends on `split-release-apks-by-abi` being applied first.** That change is where `buildTypes.release` is currently being edited, it is what leaves `isMinifyEnabled = false` in place with a comment pointing here, and it is where per-artifact baseline sizes get recorded — the figures this change measures its gate against. Applying them concurrently means editing the same block twice.

**Code and config**
- `app/build.gradle.kts` — `buildTypes.release`: `isMinifyEnabled = true`, explicit `proguardFiles`, and the verification switch.
- `app/proguard-rules.pro` — new; keep rules with the evidence behind each one.
- `Dockerfile` / `.github/workflows/release-build.yml` — publish or retain the deobfuscation mapping per release.
- `AGENTS.md` — record that release shrinking is on, how to reproduce a minified run locally, and that `connectedAndroidTest` alone no longer covers the shipped configuration (required by the existing `agent-guide` "kept current" obligation).

**No source changes.** Nothing under `app/src/` or `engine/src/` is edited; the risk this change carries is entirely about what the build does to compiled code, which is why its verification is its substance.

**Measured basis** — the ~28 MB remainder is the published 243 MB asset minus the 214.7 MB of native libraries extracted from the resolved `ffmpeg-kit-full-gpl` AAR, so nearly all of it is dex. Cross-checked against LibreCuts, which ships the same ffmpeg-kit unminified with a plain View-based UI and backs out to a consistent ~8 MB overhead three times over — the ~20 MB difference between the two projects is Compose's dex, which is the part R8 is effective on. **No shrunk build has been measured here; every saving figure is an estimate until the first one runs.**

**Out of scope** — the 89.9 MB of native libraries. Shrinking cannot touch prebuilt `.so` files, and reducing them means changing the ffmpeg-kit variant, which requires re-verifying the whole metadata-preservation matrix and collides with the open re-encode rotation gap.
