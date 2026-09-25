# Delta: metadata-preserving-cut

## ADDED Requirements

### Requirement: Location is carried exactly once, as the source wrote it

When the source carries GPS location in the QuickTime `moov/udta/©xyz` representation, the output SHALL carry location exactly once: the retained `©xyz` payload from the source, at that representation. The output SHALL NOT also carry a location produced by normalizing or re-encoding the source value — neither a 3GPP LocationInformation (`loci`) box nor a normalized location dictionary entry — because such translations re-quantize coordinates (observed drift: `+52.5182` → `52.51819`, ≈1.2 m, with a fabricated altitude of 0) and give extractors conflicting answers.

When the source carries location but not in the QuickTime `©xyz` representation, the output SHALL retain the source's location using the representation the source used; the cut SHALL NOT silently drop location for such sources.

This requirement applies identically in lossless and re-encode mode.

#### Scenario: QuickTime-location output carries a single, exact location

- **WHEN** a source carrying `moov/udta/©xyz` is cut in either mode
- **THEN** the output's location is the source `©xyz` payload, byte-for-byte
- **AND** the output contains no 3GPP LocationInformation box
- **AND** the output contains no location entry created by translating the source value

#### Scenario: No invented location box on any cut

- **WHEN** any corpus source video is cut in either mode
- **THEN** the output contains no 3GPP LocationInformation box that was absent from the source

#### Scenario: Dictionary-location sources keep their location

- **WHEN** a source whose location is carried only as a location dictionary entry (no `©xyz`) is cut
- **THEN** the output still carries the source's location value
