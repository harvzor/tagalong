## Purpose

Defines the user-facing flow that turns a picked video into a saved, metadata-correct cut: choosing a source, previewing and selecting a trim range, running a cut, and saving the result to the gallery under the date it was shot. This is the app surface built around the engine's `metadata-preserving-cut` contract; this slice covers the lossless path only.

## Requirements

### Requirement: User can select a source video

The app SHALL let the user pick a video from the device and make it the working source for a cut. The app SHALL NOT modify the picked video.

#### Scenario: Picking a video

- **WHEN** the user chooses "pick video" and selects a video from the device
- **THEN** that video becomes the working source and is presented for trimming
- **AND** the original picked file is left unmodified

#### Scenario: No video selected

- **WHEN** the user dismisses the picker without choosing a video
- **THEN** no source is set and no cut can be started

### Requirement: User can preview the source and choose a trim range

The app SHALL present a preview of the selected video together with a trim range bounded by the clip's duration. The user SHALL be able to set a start point and an end point within that duration, and the app SHALL keep the preview and the chosen range consistent as the user adjusts them.

#### Scenario: Range is bounded by the clip

- **WHEN** a source video is selected
- **THEN** the trim range spans from the start of the clip to its full duration
- **AND** the start point cannot be set later than the end point

#### Scenario: Adjusting a trim handle updates the preview

- **WHEN** the user drags a trim handle to a new position
- **THEN** the preview shows the frame at that position

### Requirement: User can run a lossless cut of the chosen range

The app SHALL let the user run a lossless cut of the selected range and SHALL perform the cut without blocking interaction (off the main thread). The produced output SHALL be a lossless cut spanning the chosen start and end, and the source SHALL remain unmodified.

#### Scenario: Cutting the selected range

- **WHEN** the user starts the cut with a chosen start and end
- **THEN** a lossless output is produced covering that range
- **AND** the source video's bytes are unchanged after the cut

### Requirement: The cut is saved to the gallery under the original capture date

The app SHALL save the cut output so it appears in the device gallery under the date the source was shot, not the date it was edited. The app SHALL confirm success to the user by showing the gallery date that was applied.

#### Scenario: Saved cut carries the original date

- **WHEN** a lossless cut completes successfully
- **THEN** the output is registered in the gallery with its gallery date equal to the source's original capture date
- **AND** the app shows the user the applied gallery date as confirmation

### Requirement: Failures are surfaced, never silent

If any step of the flow fails — reading the picked video, cutting, or saving — the app SHALL show the user that the operation failed with an explanation, and SHALL NOT present a failed cut as if it had succeeded.

#### Scenario: A failed cut is reported

- **WHEN** the cut or save step fails
- **THEN** the app shows an error explaining the failure
- **AND** the app does not report the cut as saved
