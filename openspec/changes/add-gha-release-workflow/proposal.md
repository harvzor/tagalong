## Why

The app can already be built via Docker, but there is no automated path from a git tag to a signed release APK attached to a GitHub Release. Adding a GHA workflow closes this gap and allows any tagged release to be published without manual local tooling.

## What Changes

- Add `.github/workflows/release-build.yml` that triggers on `v*` tags, builds a signed release APK via the existing Docker build, and attaches it to a GitHub Release.

## Capabilities

### New Capabilities

- `gha-release-build`: A GitHub Actions workflow that, on push of a `v*` tag, decodes the release keystore from repository secrets, runs `docker build` with signing arguments, and publishes the output APK to a GitHub Release via `softprops/action-gh-release`.

### Modified Capabilities

<!-- None — the Docker build behavior is unchanged; only a new CI trigger is added. -->

## Impact

- **New file**: `.github/workflows/release-build.yml`
- **No source code changes**: the Dockerfile and signing config in `app/build.gradle.kts` already support this flow.
- **GitHub repository secrets required**: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (same keystore and secret names as `android-bluetooth-bouncer`).
- **No minSdk / compileSdk / dependency changes.**
