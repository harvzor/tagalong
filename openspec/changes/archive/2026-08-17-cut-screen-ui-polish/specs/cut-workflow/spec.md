## ADDED Requirements

### Requirement: The app displays the picked source video's path while the user trims

While a source video is selected and the user is setting the trim range, the app SHALL display the picked video's gallery-relative path (e.g. `DCIM/Camera/PXL_20240101.mp4`). When the gallery-relative path is not available (such as when the Google Photopicker redacts `RELATIVE_PATH`), the app SHALL fall back to displaying the filename only. The path SHALL remain visible throughout the trim-and-cut flow until a new video is picked or the screen is exited.

#### Scenario: Path shown when relative path is available

- **WHEN** the user picks a video and the gallery relative path can be resolved
- **THEN** the app displays the combined relative path and filename (e.g. `DCIM/Camera/video.mp4`) above the video preview

#### Scenario: Filename shown when relative path is unavailable

- **WHEN** the user picks a video via a picker that redacts the relative path
- **THEN** the app displays the filename only (e.g. `video.mp4`) above the video preview
