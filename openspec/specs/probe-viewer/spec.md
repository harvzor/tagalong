## Purpose

Lets a user see the full metadata inventory of a video file before and after a cut, directly in the app, to verify that preservation-critical tags survived unchanged.

## Requirements

### Requirement: Source metadata shown at pick time

After a video is picked and materialized, the app SHALL display a metadata summary for the source file. The summary SHALL appear without any further user action.

#### Scenario: Source card appears after pick

- **WHEN** a video is successfully picked and processed
- **THEN** a metadata card for the source file is visible on the cut screen, showing at minimum: capture timestamp, location presence, rotation, video codec and dimensions, and any `com.*` tags

#### Scenario: No metadata card shown before pick

- **WHEN** no video has been picked
- **THEN** no metadata card is shown

### Requirement: Output metadata shown after cut

After a successful cut, the app SHALL display a second metadata card for the cut output file. It SHALL appear in the same section as the source card, below it.

#### Scenario: Output card appears after successful cut

- **WHEN** a cut completes successfully
- **THEN** a metadata card for the cut output file appears below the source metadata card

#### Scenario: Output card not shown before cut

- **WHEN** a video is picked but no cut has been performed
- **THEN** only the source metadata card is visible; no output card is shown

### Requirement: Curated summary with expandable raw tags

Each metadata card SHALL show a curated summary by default and allow the user to expand a section revealing every remaining raw tag.

#### Scenario: Curated summary fields

- **WHEN** a metadata card is displayed
- **THEN** it shows: creation_time (human-readable), location/location-eng tags (value if present, "—" if absent), rotation in degrees, video codec MIME and resolution (width × height), and all tag keys beginning with `com.` from the format, video stream, and audio stream tag maps combined

#### Scenario: Expandable raw tags

- **WHEN** the user taps the expand control on a metadata card
- **THEN** all remaining tag key-value pairs (those not shown in the curated summary) are revealed below the curated fields

#### Scenario: Collapse raw tags

- **WHEN** the raw tags section is expanded and the user taps the control again
- **THEN** the raw tag list collapses

### Requirement: Metadata probe uses existing engine capability

The app SHALL obtain metadata by calling the existing `MetadataReader.probe()` function already present in the `:engine` module. No new probe mechanism or dependency SHALL be introduced.

#### Scenario: Probe reuses engine

- **WHEN** metadata is read for source or output
- **THEN** the result is a `MediaProbe` instance produced by `MetadataReader.probe()`, operating on the materialized cache file
