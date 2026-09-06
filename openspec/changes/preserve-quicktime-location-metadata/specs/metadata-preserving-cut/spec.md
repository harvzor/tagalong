## MODIFIED Requirements

### Requirement: Lossless cut preserves all source file-level metadata

When cutting in lossless (stream-copy) mode, the engine SHALL produce an output whose container metadata retains every tag present in the source. Preservation means no source tag is lost; the engine MAY add tags of its own (e.g. an `encoder` tag). The tags that must survive include the creation date, GPS location, camera make/model and manufacturer-specific tags, and any other format- or stream-level tags carried by the source. When the source carries GPS location in the QuickTime `moov/udta/©xyz` atom, the output SHALL retain that location representation and its source payload; a normalized `mdta/location` value alone is not a substitute.

#### Scenario: Every source tag survives a lossless cut

- **WHEN** a real source video is cut in lossless mode
- **THEN** every metadata tag present in the source is present in the output with an identical value
- **AND** the output's video and audio streams use the same codecs as the source, with no re-encoding
- **AND** when the source contains `moov/udta/©xyz`, the output contains the same QuickTime location atom payload

#### Scenario: Creation date and location are retained

- **WHEN** the source carries a `creation_time` and a `location` tag
- **THEN** the output carries the same `creation_time` and the same logical `location` value
- **AND** when the source location is stored in `moov/udta/©xyz`, the output retains that representation and does not replace it solely with generic `mdta/location` metadata

### Requirement: The guarantee holds identically in both modes

The metadata-preservation guarantee SHALL hold identically whether the cut is performed in lossless mode or in re-encode mode. The choice of mode SHALL affect only pixels and speed, never whether metadata is preserved. An engine that preserves metadata in one mode but not the other SHALL be treated as failing the contract. This includes preservation of a source QuickTime `moov/udta/©xyz` location atom when one is present.

#### Scenario: Re-encode output preserves the same tags as lossless

- **WHEN** the same source is cut in re-encode mode
- **THEN** the output retains every source tag that the lossless output retains
- **AND** the output's `MediaStore.DATE_TAKEN` equals the source's original capture date
- **AND** when the source contains `moov/udta/©xyz`, the re-encoded output retains the same QuickTime location atom payload
