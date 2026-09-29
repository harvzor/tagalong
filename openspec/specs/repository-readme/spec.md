## Purpose

Defines what the repository README must communicate to someone arriving at the project for the first time — what Tagalong is, what it deliberately is not, what gets preserved, and how to install and build it.

## Requirements

### Requirement: Product statement in the opening

The README SHALL open with the app name as the heading (`Tagalong: Video Cutter`), followed by a one-sentence tagline that states the core promise, followed by a short intro paragraph that names the problem (metadata loss on trim), states what Tagalong does about it, and — in the same paragraph — makes explicit that Tagalong is not a general-purpose editor (no timeline, no filters, no cloud).

#### Scenario: Reader lands on the repo without prior context

- **WHEN** a person opens the README having never heard of Tagalong
- **THEN** they SHALL be able to answer "what does this app do and what doesn't it do?" from the first paragraph alone, without reading further

### Requirement: Metadata preservation table

The README SHALL include a section that lists every category of metadata Tagalong is obligated to preserve, with a plain-language explanation of each item's significance.

The table SHALL include at minimum: creation date, GPS location, camera information, orientation, and gallery date. The gallery date entry SHALL note that it is stored separately from container metadata and is the item most editors silently get wrong.

#### Scenario: Reader wants to know if their specific metadata will survive

- **WHEN** a reader checks whether GPS coordinates are preserved
- **THEN** they SHALL find GPS location explicitly listed with a description that makes clear it is preserved from the source

#### Scenario: Reader is unfamiliar with the gallery date distinction

- **WHEN** a reader scans the metadata table
- **THEN** the gallery date row SHALL explain that it is distinct from container-level creation date and is the thing most editors get wrong

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

### Requirement: Permissions section

The README SHALL include a permissions table listing every permission the app declares, with a plain-language explanation of why each is needed.

The `ACCESS_MEDIA_LOCATION` entry SHALL explain that Android strips GPS tags at the OS layer from any `openInputStream` call made without this permission — regardless of which file picker is used — and that the permission exists solely to receive an unredacted byte stream for the file the user explicitly selected.

#### Scenario: Reader is cautious about permissions

- **WHEN** a reader reviews the permissions table
- **THEN** each row SHALL tell them concretely why that permission is needed, with no entry left as a vague label

#### Scenario: Reader questions why ACCESS_MEDIA_LOCATION is needed

- **WHEN** a reader reads the ACCESS_MEDIA_LOCATION row
- **THEN** they SHALL understand that it prevents the OS from stripping GPS data before the app ever sees the file, and that it is used only to read the file the user selected — not to track location

### Requirement: Building and Releases sections retained

The README SHALL retain the Building section (Docker-based build, no host SDK required) and the Releases section (version-tag–triggered CI, keystore secrets) from the current README, lightly edited for consistency with the new structure. No content from those sections SHALL be removed.

Where either section states a filename the build produces, it SHALL name the outputs the build actually produces now that a build emits more than one artifact, rather than a single APK filename.

#### Scenario: Contributor wants to build from source

- **WHEN** a contributor reads the Building section
- **THEN** they SHALL find the exact `docker build` command needed and know where the APKs are written

#### Scenario: Contributor cannot find the file a section promised

- **WHEN** a contributor runs the documented build and looks for the output the README names
- **THEN** the filename they find is one the README stated

### Requirement: Re-encode mode not mentioned

The README SHALL NOT mention re-encode mode, frame-accurate cuts, or any cut mode beyond lossless. These features are not yet implemented.

#### Scenario: Reader searches the README for re-encode

- **WHEN** a reader looks for information about re-encoding or quality settings
- **THEN** they SHALL find no mention of such features in the README
