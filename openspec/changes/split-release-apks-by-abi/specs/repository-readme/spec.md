## MODIFIED Requirements

### Requirement: Install section

The README SHALL include an install section with a link to GitHub Releases. Because a release publishes more than one downloadable artifact, the section SHALL state which artifact a reader should download, and SHALL make that decidable without the reader knowing the term for, or the name of, their device's CPU architecture.

The section SHALL name every artifact a release publishes, say in ordinary language which devices each one is for, and offer the combined artifact as the choice for a reader who does not want to decide.

No setup wizard or dependency steps are required beyond installing the APK.

#### Scenario: Reader wants to try the app

- **WHEN** a reader wants to install Tagalong
- **THEN** they SHALL find a direct link to GitHub Releases
- **AND** they SHALL be able to tell which of the published artifacts to download without looking anything up

#### Scenario: Reader cannot tell which artifact fits

- **WHEN** a reader cannot tell, or does not want to tell, which artifact matches their device
- **THEN** the section SHALL name a single artifact that works regardless

#### Scenario: Reader needs to switch artifact after installing

- **WHEN** a reader has installed one artifact and needs to install a different one
- **THEN** the section SHALL tell them to remove the first install before installing the second

#### Scenario: Reader's device is not supported

- **WHEN** a reader's device accepts none of the published artifacts
- **THEN** the section SHALL make clear that Android rejected the install because the device is unsupported, not because the download is damaged

### Requirement: Building and Releases sections retained

The README SHALL retain the Building section (Docker-based build, no host SDK required) and the Releases section (version-tag–triggered CI, keystore secrets) from the current README, lightly edited for consistency with the new structure. No content from those sections SHALL be removed.

Where either section states a filename the build produces, it SHALL name the outputs the build actually produces now that a build emits more than one artifact, rather than a single APK filename.

#### Scenario: Contributor wants to build from source

- **WHEN** a contributor reads the Building section
- **THEN** they SHALL find the exact `docker build` command needed and know where the APKs are written

#### Scenario: Contributor cannot find the file a section promised

- **WHEN** a contributor runs the documented build and looks for the output the README names
- **THEN** the filename they find is one the README stated
