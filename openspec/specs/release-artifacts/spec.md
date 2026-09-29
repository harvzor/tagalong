## Purpose

Defines the set of installable artifacts published with each Tagalong release: which CPU architectures each artifact carries, how a user picks between them, and what happens when they pick wrong.

## Requirements

### Requirement: Release publishes one artifact per supported architecture

Each release SHALL publish one installable artifact for every supported CPU architecture, and every artifact SHALL carry native libraries for the architecture it names and no others.

The supported architectures SHALL be `arm64-v8a` and `x86_64`.

#### Scenario: Per-architecture artifact exists

- **WHEN** a release is published
- **THEN** a distinct installable artifact is available for each supported architecture

#### Scenario: An artifact carries only its own architecture's native code

- **WHEN** the native library contents of the `arm64-v8a` artifact are inspected
- **THEN** only `arm64-v8a` native libraries are present
- **AND** no native library for any other architecture is present

### Requirement: Universal artifact covers every supported architecture

Each release SHALL also publish a `universal` artifact containing the native libraries of every supported architecture, so that a user can install a working artifact without identifying their own device's architecture.

The architectures the `universal` artifact covers SHALL be exactly those shipped as separate artifacts; the `universal` artifact SHALL NOT be more permissive than the sum of them.

#### Scenario: Universal artifact installs on every supported architecture

- **WHEN** the `universal` artifact is installed on a device of any supported architecture
- **THEN** installation succeeds and the app runs

#### Scenario: Universal artifact is not broader than the shipped set

- **WHEN** the native library contents of the `universal` artifact are inspected
- **THEN** it contains exactly the architectures published as separate artifacts, and no others

### Requirement: Unsupported architectures are absent from every artifact

No published artifact SHALL contain native libraries for an architecture outside the supported set. An installation attempt on a device whose only architecture is unsupported SHALL fail at install time with Android's incompatibility rejection, and SHALL NOT install and fail later.

#### Scenario: Unsupported device cannot install

- **WHEN** any published artifact is offered to a device that supports only an unsupported architecture
- **THEN** Android rejects the installation before any code runs
- **AND** the user sees an incompatibility message rather than a broken install

### Requirement: Artifact filenames identify their architecture

Every published artifact SHALL be named so that its architecture is readable from the filename alone, including a distinct name for the `universal` artifact. No two published artifacts of a release SHALL share a filename.

#### Scenario: Architecture is readable from the name

- **WHEN** a release's assets are listed
- **THEN** each filename states the version and the architecture or `universal`
- **AND** no two assets have the same name

### Requirement: Cutting behaves identically regardless of which artifact was installed

The artifact a user happens to install SHALL NOT affect cut behaviour or preserved metadata. Output produced by the app SHALL satisfy the metadata-preservation contract identically whichever supported artifact was installed.

#### Scenario: Same source, different artifact, same tags survive

- **WHEN** the same source video is cut by builds of two different artifacts of the same release
- **THEN** the set of metadata tags surviving to the output is identical in both
