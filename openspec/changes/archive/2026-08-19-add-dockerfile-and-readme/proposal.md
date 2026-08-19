## Why

Tagalong has no documented build process and no README. Adding a Docker-based build makes the project buildable on any machine with Docker installed — no Android SDK, JDK, or Gradle required on the host — and a README makes the project usable by anyone who finds it.

## What Changes

- Add `Dockerfile` at the project root — hermetic, multi-stage, BuildKit-optimised build producing a debug or signed release APK
- Add `.dockerignore` — excludes host-specific and generated files from the build context
- Add `signingConfigs` block to `app/build.gradle.kts` — reads signing material from Gradle project properties passed by the Dockerfile at build time; degrades gracefully to debug signing when properties are absent
- Add `README.md` — covers what the app does, why it uses `ACTION_OPEN_DOCUMENT`, and how to build locally with Docker (debug and signed release)

## Capabilities

### New Capabilities
- `docker-build`: Hermetic Docker build producing an APK from the tagalong source tree

### Modified Capabilities
<!-- None -->

## Impact

- `Dockerfile`: new file, project root
- `.dockerignore`: new file, project root
- `app/build.gradle.kts`: `signingConfigs` and `defaultConfig` version logic added; no existing behaviour changed
- `README.md`: new file, project root
- No `:engine` source changes
