## ADDED Requirements

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
