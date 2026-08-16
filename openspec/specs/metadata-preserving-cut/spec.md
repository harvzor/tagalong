## Purpose

Defines the behavioral contract for cutting a video while preserving the record of when and where it was shot. This is tagalong's core guarantee; the cut-engine bake-off validates that a candidate engine can satisfy every requirement here on a real device before the app is built around it.

## Requirements

### Requirement: Lossless cut preserves all source file-level metadata

When cutting in lossless (stream-copy) mode, the engine SHALL produce an output whose container metadata retains every tag present in the source. Preservation means no source tag is lost; the engine MAY add tags of its own (e.g. an `encoder` tag). The tags that must survive include the creation date, GPS location, camera make/model and manufacturer-specific tags, and any other format- or stream-level tags carried by the source.

#### Scenario: Every source tag survives a lossless cut

- **WHEN** a real source video is cut in lossless mode
- **THEN** every metadata tag present in the source is present in the output with an identical value
- **AND** the output's video and audio streams use the same codecs as the source, with no re-encoding

#### Scenario: Creation date and location are retained

- **WHEN** the source carries a `creation_time` and a `location` tag
- **THEN** the output carries the same `creation_time` and the same `location`

### Requirement: Gallery date is preserved

The output SHALL appear in the device gallery under the date the video was shot, not the date it was edited. This is a system-level obligation distinct from container metadata: the engine SHALL set `MediaStore.DATE_TAKEN` on the output to the source's original capture date, and that value SHALL survive a media scan.

#### Scenario: Output shows original date in the gallery

- **WHEN** a cut output is written and registered with the media store
- **THEN** its `MediaStore.DATE_TAKEN` equals the source's original capture date
- **AND** the value is unchanged after the file is scanned by the media scanner

### Requirement: Orientation is preserved as a signal, not baked into frames

The engine SHALL preserve the source's orientation by carrying through the rotation signal (e.g. the display matrix / rotation tag) rather than physically rotating the pixels. A portrait clip SHALL remain portrait without the frames being re-oriented.

#### Scenario: Rotation metadata carries through

- **WHEN** the source declares a rotation (e.g. `rotation=-90`)
- **THEN** the output declares the same rotation
- **AND** the raw frame dimensions are unchanged from the source

### Requirement: The guarantee holds identically in both modes

The metadata-preservation guarantee SHALL hold identically whether the cut is performed in lossless mode or in re-encode mode. The choice of mode SHALL affect only pixels and speed, never whether metadata is preserved. An engine that preserves metadata in one mode but not the other SHALL be treated as failing the contract.

#### Scenario: Re-encode output preserves the same tags as lossless

- **WHEN** the same source is cut in re-encode mode
- **THEN** the output retains every source tag that the lossless output retains
- **AND** the output's `MediaStore.DATE_TAKEN` equals the source's original capture date

### Requirement: The original file is never modified

The engine SHALL always write a new output file and SHALL never modify the source. After any cut, the source's bytes and metadata SHALL be unchanged.

#### Scenario: Source is untouched after a cut

- **WHEN** a cut completes in either mode
- **THEN** a new output file exists distinct from the source
- **AND** the source file's contents and metadata are identical to before the cut
