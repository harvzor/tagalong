## Context

The Dockerfile already accepts `BUILD_TYPE=release`, `VERSION`, and four signing secrets via `--mount=type=secret`. The `app/build.gradle.kts` signing config reads them as Gradle properties. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One workflow file that goes from tag to published GitHub Release with zero manual steps.
- Keystore bytes never appear in any image layer, workflow log, or artifact.

**Non-Goals:**
- Debug / PR builds (separate concern, not part of this change).
- Play Store upload (sideload / direct APK distribution is sufficient for now).
- Matrix builds or multiple ABIs.

## Decisions

### Use the existing Docker build rather than a native Gradle step

The Docker build already produces a correctly signed, reproducible APK and is tested locally. Reusing it means the GHA environment is just a Docker host — no JDK, Android SDK, or Gradle cache setup needed on the runner.

*Alternative considered:* Run `./gradlew assembleRelease` directly on the runner. Rejected because it requires installing JDK + Android SDK on the runner, adds cache management, and duplicates logic already proven in Docker.

### Use `softprops/action-gh-release@v2` for release creation

Widely used, handles both creating and updating a release from a tag, and supports glob file patterns. The same action is already used in `android-bluetooth-bouncer`.

### Decode keystore to a temp file before the Docker build step

`docker build --secret id=keystore,src=<path>` requires a local file. The base64-encoded keystore stored in `RELEASE_KEYSTORE_BASE64` is decoded once to `release.keystore` in the workspace, consumed by Docker's secret mount (never baked into a layer), and not uploaded as a workflow artifact.

### Reuse the same keystore as `android-bluetooth-bouncer`

Both apps are signed by the same developer. A single keystore with alias `release` covers both apps; the Play Store associates each key with a package ID independently. This avoids generating and managing a second keystore.

## Risks / Trade-offs

- **Secret misconfiguration** → Docker build will fail at the signing step with a Gradle error. Mitigation: the README documents the four required secrets and how to populate them with `gh secret set`.
- **Keystore loss** → The `.keystore` file lives only locally (gitignored) and in the `RELEASE_KEYSTORE_BASE64` secret. If both are lost, the app cannot be updated on the Play Store under the same package ID. Mitigation: back up the keystore to a password manager or offline storage.
