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

After a successful cut, the app SHALL display the cut output's metadata as part of the unified diff card on the result screen. The diff card SHALL appear on the result screen only — not on the trim screen. No separate output-only metadata card SHALL be shown anywhere in the app.

#### Scenario: Output metadata appears on result screen after cut

- **WHEN** a cut completes successfully and the user is on the result screen
- **THEN** the metadata diff card shows cut output values alongside source values for every curated tag

#### Scenario: Output metadata not shown on trim screen

- **WHEN** a cut completes successfully and the user navigates back to the trim screen
- **THEN** no output metadata card is shown on the trim screen

#### Scenario: Output card not shown before cut

- **WHEN** a video is picked but no cut has been performed
- **THEN** only the source metadata card is visible on the trim screen; no output metadata is shown anywhere

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

### Requirement: Container representation of preservation-critical metadata is visible

The metadata viewer SHALL distinguish a logical metadata value from the physical container representation that stores it when that distinction affects compatibility. For GPS location, the viewer SHALL identify whether the source or output contains the QuickTime `moov/udta/©xyz` location atom, generic `mdta/location` or `mdta/location-eng` entries, both, or neither. A logical location value reported by FFprobe SHALL NOT by itself be presented as proof that every compatibility-relevant location representation was preserved.

#### Scenario: Source uses a QuickTime location atom

- **WHEN** a source video contains a `moov/udta/©xyz` atom
- **THEN** the source metadata card identifies the location value and the QuickTime `©xyz` representation
- **AND** the representation is visible without requiring the user to inspect the file externally

#### Scenario: Output has a logical location but loses the QuickTime representation

- **WHEN** the source contains `moov/udta/©xyz` and the output contains only generic `mdta/location` metadata
- **THEN** the source/output diff identifies the physical location representation as changed or missing
- **AND** the diff does not report all preservation-critical metadata as unchanged

#### Scenario: Output retains multiple location representations

- **WHEN** the output contains both `moov/udta/©xyz` and generic `mdta` location entries
- **THEN** the viewer identifies both representations
- **AND** the logical location value and each representation are shown consistently for source and output

#### Scenario: A video has no embedded location

- **WHEN** neither the source nor output contains a recognized embedded location representation
- **THEN** the viewer displays that location is absent rather than treating an empty or unavailable logical tag as preserved
