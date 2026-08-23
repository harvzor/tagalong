## Purpose

Provides an automated GitHub Actions workflow that produces a signed release APK and attaches it to a GitHub Release whenever a version tag is pushed.

## ADDED Requirements

### Requirement: Workflow triggers on version tags
The workflow SHALL trigger on any push of a tag matching `v*` and SHALL NOT run on branch pushes or pull requests.

#### Scenario: Version tag push triggers build
- **WHEN** a tag matching `v*` (e.g. `v1.0.0`) is pushed to the repository
- **THEN** the release build workflow starts automatically

#### Scenario: Non-tag push does not trigger build
- **WHEN** a commit is pushed to a branch (not a tag)
- **THEN** the release build workflow does NOT run

### Requirement: Release APK signed using repository secrets
The workflow SHALL sign the APK using a release keystore supplied via the following four repository-level secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The keystore SHALL NOT be stored in any Docker image layer or workflow artifact.

#### Scenario: Signed APK produced when secrets are configured
- **WHEN** the four required secrets are set in the repository and the workflow triggers
- **THEN** the Docker build produces a signed release APK in `out/`

### Requirement: APK version derived from the git tag
The workflow SHALL extract the version string by stripping the leading `v` from the tag name (e.g. `v1.2.3` → `1.2.3`) and SHALL pass it to the Docker build as `VERSION`, resulting in an output file named `tagalong-<version>.apk`.

#### Scenario: Tag version appears in APK filename
- **WHEN** the tag `v0.9.0` is pushed
- **THEN** the output file is named `tagalong-0.9.0.apk`

### Requirement: APK attached to a GitHub Release
The workflow SHALL create a GitHub Release for the triggering tag and SHALL upload the signed APK as a release asset.

#### Scenario: Release created with APK asset
- **WHEN** the workflow completes successfully
- **THEN** a GitHub Release exists for the tag with the APK file attached as a downloadable asset
