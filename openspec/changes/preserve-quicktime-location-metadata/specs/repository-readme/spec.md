## MODIFIED Requirements

### Requirement: How It Works section

The README SHALL include a "How It Works" section that explains, in plain language:

1. Why Tagalong uses ffmpeg-kit instead of Media3 Transformer (Media3 drops metadata tags and overwrites `creation_time`)
2. Why Tagalong uses `ACTION_OPEN_DOCUMENT` instead of the Photo Picker (Photo Picker strips GPS, replaces the filename with a numeric ID, and nulls the gallery path)
3. The keyframe-snapping constraint of lossless cuts — that trim points snap to the nearest keyframe, not the exact slider position, and that this is a real constraint the UI surfaces rather than silently working around
4. Why a logical FFprobe `location` tag is not sufficient for compatibility, including that device-originated samples may store GPS in the QuickTime `moov/udta/©xyz` atom while a generic `mdta/location` entry may be ignored by gallery applications such as Google Photos

This section SHALL be framed as explanation for a curious reader, not as a justification for a reviewer.

#### Scenario: Reader wonders why the Photo Picker wasn't used

- **WHEN** a reader reads "How It Works"
- **THEN** they SHALL understand why ACTION_OPEN_DOCUMENT is used and what specific data the Photo Picker would have lost

#### Scenario: Reader wonders why ffmpeg-kit was chosen over Media3

- **WHEN** a reader reads "How It Works"
- **THEN** they SHALL understand that Media3 Transformer overwrites metadata and that this made it incompatible with Tagalong's core contract

#### Scenario: Reader is surprised the trim point doesn't land exactly

- **WHEN** a reader reads "How It Works"
- **THEN** they SHALL find the keyframe-snapping behaviour explained and framed as an inherent lossless-cut constraint, not a bug

#### Scenario: Reader wants to understand GPS compatibility

- **WHEN** a reader reads the metadata explanation
- **THEN** they SHALL understand that FFprobe's normalized `location` output can hide the underlying MP4 atom type
- **AND** they SHALL understand that the QuickTime `©xyz` representation is retained because compatible gallery applications may depend on it
