## Context

See proposal.md — Why. The approach is a direct port of the `android-bluetooth-bouncer` Dockerfile, adapted for tagalong's two-module structure (`:app` + `:engine`).

Key constraints:
- AGP 9.2.1 / Gradle 9.5.0 — the wrapper is committed; the Dockerfile uses it
- `CLAUDE.md` forbids `kotlinOptions {}` and `org.jetbrains.kotlin.android` — the signing block in `app/build.gradle.kts` must not introduce either
- `com.android.library` in the root `build.gradle.kts` is still required by `:engine` and must not be removed

## Goals / Non-Goals

**Goals:**
- Hermetic debug build with a single `docker build --output=out .` command
- Signed release build via BuildKit secrets, matching the bouncer pattern
- `app/build.gradle.kts` signing config degrades gracefully to debug signing when secrets are absent
- `README.md` documents the debug and release build commands

**Non-Goals:**
- GitHub Actions workflow (deferred)
- AAB / Play Store output (APK only for now)
- Instrumented tests inside Docker (no emulator in the build container)

## Decisions

### D1: Port bouncer's Dockerfile directly

The bouncer Dockerfile is proven and already solves the hard problems (SDK layer caching, BuildKit secrets, scratch export stage). The only structural difference is tagalong has two modules that need stub manifests and source copies.

**Stub phase** — copies build config files and writes minimal `AndroidManifest.xml` stubs so AGP can configure both modules during dependency resolution without any source:
```
COPY app/build.gradle.kts   app/build.gradle.kts
COPY engine/build.gradle.kts engine/build.gradle.kts
RUN mkdir -p app/src/main engine/src/main
    + write stub manifests for both
```

**Source phase** — copies full module source after the dependency warmup layer:
```
COPY app/    app/
COPY engine/ engine/
```

### D2: signingConfigs reads Gradle project properties

Identical pattern to bouncer. `findProperty("releaseKeystorePath")` returns null when the property is absent, so the `"release"` signing config is never created for a debug build. `signingConfigs.findByName("release")` in `buildTypes.release` then returns null, and AGP falls back to debug signing automatically. No extra conditional logic needed.

### D3: versionCode derived from VERSION arg

Bouncer derives `versionCode` from `appVersionName` using `parsedParts[0] * 10_000 + parsedParts[1] * 100 + parsedParts[2]`. Tagalong adopts the same formula so version bumps stay in one place (the git tag / `-PappVersionName` flag). Fallback: `versionCode = 1`, `versionName = "0.0.0-dev"` when the property is absent (debug builds).

### D4: .dockerignore excludes androidTest assets

The `engine/src/androidTest/` directory may contain test fixture assets. These are not needed for a release build and should not inflate the build context. The `.dockerignore` adds `**/src/androidTest/` alongside the standard entries from bouncer.

### D5: README covers Building only

No GHA section. The README documents two commands: debug build and signed release build. The signing setup (keytool + gh secret set) is omitted until GHA is added.

## Risks / Trade-offs

- **Risk**: ffmpeg-kit-full-gpl is a large AAR (~tens of MB of native libs). First build is slow.  
  → **Mitigation**: Layer D2 (dependency warmup) caches it; subsequent source-only rebuilds skip it entirely.

- **Risk**: The stub manifest approach requires Gradle to successfully configure both modules from build.gradle.kts alone, before any source exists. If either module's build.gradle.kts references a source file at configuration time, the stub phase fails.  
  → **Mitigation**: Both modules' build.gradle.kts files are purely declarative — no source-file references at configuration time. Confirmed working in bouncer's equivalent pattern.
