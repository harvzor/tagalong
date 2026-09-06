## Purpose

Provides a canonical, extensible collection of device-originated sample videos and ensures every supported sample is included in the metadata-preservation verification matrix.

## ADDED Requirements

### Requirement: Canonical sample-video corpus

The repository SHALL use `sample-videos/` as the canonical source directory for device-originated video samples used by instrumented tests and emulator seeding. Every supported video file in that directory SHALL be available to those workflows without requiring a per-video test-code change.

#### Scenario: A new device sample is added

- **WHEN** a supported video is added to `sample-videos/`
- **THEN** the video is included in the instrumented-test inputs and emulator-seeding inputs
- **AND** no test source change is required solely to register the new filename

#### Scenario: Non-video repository files are present

- **WHEN** a file in `sample-videos/` does not have a supported video format
- **THEN** it is not treated as a sample video by the test or emulator-seeding workflows

### Requirement: Engine contract coverage spans every sample

The engine metadata-preservation contract SHALL be evaluated independently for every supported sample video in both lossless and re-encode modes. The assertions SHALL compare each output with the metadata and dimensions of that sample's own source.

#### Scenario: Every sample is tested in both cut modes

- **WHEN** the engine instrumented contract suite runs
- **THEN** each supported sample video receives one lossless cut and one re-encode cut
- **AND** each cut is checked for source immutability, source-tag preservation, creation date, location, orientation, dimensions, and gallery date

#### Scenario: One sample fails the contract

- **WHEN** a contract assertion fails for a sample video
- **THEN** the failure identifies the sample filename and cut mode
- **AND** the remaining samples retain independent test inputs and outputs

### Requirement: App end-to-end coverage spans every sample

The app end-to-end metadata test SHALL exercise each supported sample video through the pick, cut, save, and output-verification flow. The file picker selection SHALL be tied to the sample currently under test rather than to a single hard-coded device filename.

#### Scenario: A sample completes the end-to-end flow

- **WHEN** a supported sample is tested through the app flow
- **THEN** the sample is seeded, selected, cut, and verified against its source metadata
- **AND** the output retains the source creation time and all source format tags required by the metadata contract

#### Scenario: Multiple samples are tested sequentially

- **WHEN** the end-to-end suite advances from one sample to the next
- **THEN** the previous sample's MediaStore rows, cache files, and outputs are cleaned up or isolated
- **AND** the next sample cannot be mistaken for the previous sample by the picker or assertions

### Requirement: Sample identity is preserved in verification artifacts

The test workflows SHALL preserve each sample's filename as its stable identity for diagnostics, temporary files, generated outputs, and emulator-seeding messages. A sample's identity SHALL NOT be replaced by a generic fixture name.

#### Scenario: Diagnostics identify the originating sample

- **WHEN** a sample cut or end-to-end verification emits a result or failure
- **THEN** the result identifies the sample filename and the operation being verified
