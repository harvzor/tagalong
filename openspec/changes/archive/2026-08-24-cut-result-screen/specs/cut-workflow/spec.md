## MODIFIED Requirements

### Requirement: The app displays the picked source video's path while the user trims

While a source video is selected and the user is setting the trim range, the app SHALL display the full absolute storage path of the picked video (e.g. `/storage/emulated/0/DCIM/Camera/PXL_20240101.mp4`). When the absolute path cannot be resolved, the app SHALL fall back to the gallery-relative path; when neither is available, it SHALL fall back to the real filename only. The path SHALL remain visible throughout the trim-and-cut flow until a new video is picked or the screen is exited.

#### Scenario: Absolute path shown when resolvable

- **WHEN** the user picks a video and the absolute storage path can be resolved
- **THEN** the app displays the full absolute path (e.g. `/storage/emulated/0/DCIM/Camera/video.mp4`) above the video preview

#### Scenario: Gallery-relative path shown when absolute path is not resolvable

- **WHEN** the user picks a video and the absolute path cannot be resolved but the gallery-relative path can
- **THEN** the app displays the gallery-relative path (e.g. `DCIM/Camera/video.mp4`) above the video preview

#### Scenario: Filename shown when neither path form is available

- **WHEN** the user picks a video and neither the absolute path nor the gallery-relative path can be resolved
- **THEN** the app displays the real filename only (e.g. `video.mp4`) above the video preview

### Requirement: The cut is saved to the gallery under the original capture date

The app SHALL save the cut output so it appears in the device gallery under the date the source was shot, not the date it was edited. After a successful save the app SHALL navigate to the result screen; the confirmation of the gallery date SHALL be displayed on the result screen rather than on the trim screen.

#### Scenario: Saved cut carries the original date

- **WHEN** a lossless cut completes successfully
- **THEN** the output is registered in the gallery with its gallery date equal to the source's original capture date
- **AND** the app navigates to the result screen

#### Scenario: Failed cut does not navigate

- **WHEN** the cut or save step fails
- **THEN** the app shows an error on the trim screen and does not navigate to the result screen
