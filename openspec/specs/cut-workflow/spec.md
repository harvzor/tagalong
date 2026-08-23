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

The app SHALL save the cut output so it appears in the device gallery under the date the source was shot, not the date it was edited. After a successful save the app SHALL navigate to the result screen; the confirmation of the gallery date SHALL be displayed on the result screen rather than on the trim screen.

#### Scenario: Saved cut carries the original date

- **WHEN** a lossless cut completes successfully
- **THEN** the output is registered in the gallery with its gallery date equal to the source's original capture date
- **AND** the app navigates to the result screen

#### Scenario: Failed cut does not navigate

- **WHEN** the cut or save step fails
- **THEN** the app shows an error on the trim screen and does not navigate to the result screen

### Requirement: The saved output is named for its source and trim bounds

The app SHALL derive the saved output's display name from the source video's base name and the start and end timestamps of the cut, using the format `{originalBaseName}_from_{HH-MM-SS-mmm}_to_{HH-MM-SS-mmm}.mp4`. Time values SHALL use `-` as the only separator (colon-free, file-safe on all platforms). Millisecond precision SHALL be included as a 3-digit value following the seconds field. The source extension is stripped from the base name; the output extension is always `.mp4`.

#### Scenario: Display name encodes source and bounds

- **WHEN** the user saves a cut of `xiaomi-poco-x5.mp4` from 500 ms to 3 500 ms
- **THEN** the saved file's display name is `xiaomi-poco-x5_from_00-00-00-500_to_00-00-03-500.mp4`

#### Scenario: Display name for a cut starting beyond one minute

- **WHEN** the user saves a cut that starts at 1 h 22 min 10.750 s and ends at 1 h 25 min 44.000 s
- **THEN** the saved file's display name is `{originalBaseName}_from_01-22-10-750_to_01-25-44-000.mp4`

### Requirement: GPS location metadata is preserved through the pick-and-cut flow

The app SHALL declare and request the `ACCESS_MEDIA_LOCATION` runtime permission so that Android's media framework delivers an unredacted byte stream when the picked video is materialised to the local cache. With this permission granted, location tags in the source container are present in the bytes the cut engine reads and are copied to the output by the engine's normal metadata-copy path, with no manual tag injection.

The permission SHALL be requested before the file picker is launched. If the user denies the permission, the app SHALL still allow picking and cutting; the user SHALL be shown a one-time warning that GPS location metadata may not appear in the cut output. The warning SHALL be shown only when the permission was denied, not on every pick.

#### Scenario: Location tag is preserved when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **AND** the source video contains a GPS location tag in its container metadata
- **THEN** the cut output contains the same GPS location tag with an identical value

#### Scenario: Pick proceeds after permission is denied

- **WHEN** the user denies the `ACCESS_MEDIA_LOCATION` permission request
- **THEN** the app continues to the file picker without blocking the flow
- **AND** the app shows a one-time warning that GPS location may not be preserved in the output

#### Scenario: Warning is not shown when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **THEN** no location-warning message is displayed to the user

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

### Requirement: Failures are surfaced, never silent

If any step of the flow fails — reading the picked video, cutting, or saving — the app SHALL show the user that the operation failed with an explanation, and SHALL NOT present a failed cut as if it had succeeded.

#### Scenario: A failed cut is reported

- **WHEN** the cut or save step fails
- **THEN** the app shows an error explaining the failure
- **AND** the app does not report the cut as saved

### Requirement: User can nudge a trim handle by a fixed step

The app SHALL provide nudge controls for each trim handle (Start and End). Each handle SHALL have four nudge buttons: −1 s, −0.1 s, +0.1 s, and +1 s. Activating a nudge button SHALL shift the corresponding handle by the labelled step, seek the video preview to the new position immediately (no debounce), and update the time label in the same interaction. A nudge that would move a handle past the clip boundary (0 or full duration) SHALL clamp silently at the boundary; the operation SHALL succeed without error. A nudge that would cause the Start handle to meet or exceed the End handle (or vice versa) SHALL be rejected silently; the handle SHALL remain at its current position.

#### Scenario: Nudge shifts handle and seeks preview

- **WHEN** the user activates a nudge button for a trim handle
- **THEN** that handle's position changes by the step amount
- **AND** the video preview seeks to the handle's new position immediately

#### Scenario: Nudge clamps at clip start

- **WHEN** the Start handle is at 0.3 s and the user activates −1 s
- **THEN** the Start handle moves to 0.0 s and does not go below zero

#### Scenario: Nudge clamps at clip end

- **WHEN** the End handle is 0.5 s before the clip's duration and the user activates +1 s
- **THEN** the End handle moves to the clip's full duration and does not exceed it

#### Scenario: Nudge is rejected when it would cross the other handle

- **WHEN** the Start handle is 0.05 s before the End handle and the user activates +0.1 s on Start
- **THEN** the Start handle does not move

### Requirement: User can enter an exact trim time by typing

The app SHALL allow the user to directly type a time value for each trim handle (Start and End). Tapping the displayed time value for a handle SHALL open an editable text field pre-filled with the current time in `M:SS.t` format (e.g. `1:13.3`). Only this exact format SHALL be accepted: one or more digits for minutes, a colon, exactly two digits for seconds (00–59), a period, and exactly one digit for tenths. While the text in the field does not match this format or represents a value outside the valid range, the field SHALL display a visual error indicator and the value SHALL NOT be applied. Submitting a valid time via the Done IME action SHALL apply the new time to the handle and seek the video preview to that position. Dismissing the field without submitting (back gesture or tapping outside) SHALL revert the field to the handle's prior value and close the edit mode without changing the trim position. Only one handle's field SHALL be in edit mode at a time; opening one SHALL close any other that is already open.

#### Scenario: Tapping a time label opens an editable field

- **WHEN** the user taps the time label for the Start or End handle
- **THEN** a text input field appears pre-filled with the current time in M:SS.t format
- **AND** the device keyboard is shown

#### Scenario: Valid time is committed on Done

- **WHEN** the user types a valid M:SS.t value and activates the Done action
- **THEN** the handle moves to the entered time
- **AND** the video preview seeks to that position
- **AND** the text field closes

#### Scenario: Invalid format shows error state and is not committed

- **WHEN** the text in the edit field does not match M:SS.t format
- **THEN** a visual error indicator is shown on the field
- **AND** the handle remains at its current position

#### Scenario: Time beyond clip duration is rejected

- **WHEN** the user types a valid M:SS.t value that exceeds the clip's duration
- **THEN** a visual error indicator is shown on the field
- **AND** the handle remains at its current position

#### Scenario: Typed time that would cross the other handle is rejected

- **WHEN** the user types a valid M:SS.t time for Start that is greater than or equal to the current End time
- **THEN** a visual error indicator is shown on the field
- **AND** the Start handle remains at its current position

#### Scenario: Dismissing the field reverts to the prior value

- **WHEN** the user dismisses the edit field without submitting (back gesture or tap outside)
- **THEN** the field closes with no change to the trim position

