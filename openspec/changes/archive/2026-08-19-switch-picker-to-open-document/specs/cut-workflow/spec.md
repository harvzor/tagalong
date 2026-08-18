## MODIFIED Requirements

### Requirement: GPS location metadata is preserved through the pick-and-cut flow

The app SHALL preserve GPS location metadata that is present in source video container tags. The app SHALL use a file picker that provides unredacted byte-stream access to the selected video, so that location tags are present in the bytes the cut engine reads and are copied through to the output by the engine's normal metadata-copy path. No permission workaround or manual tag injection SHALL be required.

#### Scenario: Location tag is preserved in the cut output

- **WHEN** the source video contains a GPS location tag in its container metadata
- **THEN** the cut output contains the same GPS location tag

### Requirement: The app displays the picked source video's path while the user trims

While a source video is selected and the user is setting the trim range, the app SHALL display the picked video's gallery-relative path (e.g. `DCIM/Camera/PXL_20240101.mp4`). When the gallery-relative path is not available, the app SHALL fall back to displaying the real filename only. The path SHALL remain visible throughout the trim-and-cut flow until a new video is picked or the screen is exited.

#### Scenario: Path shown when relative path is available

- **WHEN** the user picks a video and the gallery relative path can be resolved
- **THEN** the app displays the combined relative path and filename (e.g. `DCIM/Camera/video.mp4`) above the video preview

#### Scenario: Filename shown when relative path is unavailable

- **WHEN** the user picks a video and the gallery relative path cannot be resolved
- **THEN** the app displays the real filename only (e.g. `video.mp4`) above the video preview

## REMOVED Requirements

### Requirement: (none removed)

*No requirements are removed. The GPS and path-display requirements above supersede their existing counterparts in the main spec.*
