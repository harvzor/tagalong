## MODIFIED Requirements

### Requirement: Release APK signed using repository secrets

The workflow SHALL sign every release APK using a release keystore supplied via the following four repository-level secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Signing SHALL apply to each APK the build produces, not only one of them. The keystore SHALL NOT be stored in any Docker image layer or workflow artifact.

#### Scenario: Signed APK produced when secrets are configured

- **WHEN** the four required secrets are set in the repository and the workflow triggers
- **THEN** the Docker build produces release APKs in `out/`, and every one of them is signed with the provided keystore

### Requirement: APK version derived from the git tag

The workflow SHALL extract the version string by stripping the leading `v` from the tag name (e.g. `v1.2.3` → `1.2.3`) and SHALL pass it to the Docker build as `VERSION`, resulting in output files named `tagalong-<version>-<identifier>.apk`.

#### Scenario: Tag version appears in APK filename

- **WHEN** the tag `v0.9.0` is pushed
- **THEN** every APK asset attached to the resulting release carries `0.9.0` in its filename
- **AND** each filename additionally identifies the architecture it targets, or `universal`

### Requirement: APK attached to a GitHub Release

The workflow SHALL create a GitHub Release for the triggering tag and SHALL upload every APK produced by the build as a release asset.

#### Scenario: Release created with APK asset

- **WHEN** the workflow completes successfully
- **THEN** a GitHub Release exists for the tag
- **AND** every APK in `out/` is attached as a separately downloadable asset
- **AND** no produced APK is missing from the release

#### Scenario: One missing artifact is visible as a failure

- **WHEN** the build produces an APK that is not attached to the release
- **THEN** the run is treated as incomplete rather than successful
