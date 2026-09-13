# share-intake (delta)

## Purpose

Lets the user start a cut from the video itself rather than from inside the app: the system share sheet delivers a user-selected video to Tagalong, which takes it straight to the trim screen and preserves every tag present in the byte stream it receives.

## ADDED Requirements

### Requirement: Tagalong is offered as a video share target

The app SHALL appear as a target in the system share sheet when the user shares video content from another app. Accepting a shared video SHALL NOT require any permission beyond those the app already declares.

#### Scenario: Share sheet offers Tagalong for a video

- **WHEN** the user selects a video in a gallery or file app and opens the system share sheet
- **THEN** Tagalong is listed as a share target

#### Scenario: Non-video shares do not open the editor

- **WHEN** another app shares content that is not video
- **THEN** Tagalong is not offered as a target for that content

### Requirement: A shared video opens directly at the trim screen

When the app is launched by a share of a usable video, the app SHALL open at the Trim screen with that video loaded as the working source, without displaying the Home screen first. The shared video becomes the working source under the same rules as an in-app pick: the received bytes are left unmodified and the trim range spans the clip's duration.

#### Scenario: Share from the gallery lands on Trim

- **WHEN** the user shares a video to Tagalong from the gallery
- **THEN** the app opens at the Trim screen with the shared video loaded and previewable
- **AND** the Home screen is not displayed

#### Scenario: Shared source is not modified

- **WHEN** a shared video is loaded and later cut
- **THEN** the bytes received from the sending app are unchanged

### Requirement: The share intent is consumed exactly once

The app SHALL treat a delivered share as one intake event. Returning to the app through the launcher, or the app being recreated while a session is alive, SHALL NOT restart the flow from a stale share intent; the existing session resumes at the screen the user left. Sharing a new video while the app is already running SHALL replace the working source with the newly shared video.

#### Scenario: Launcher relaunch does not replay the share

- **WHEN** the user shared a video, navigated away, and later reopens Tagalong from the launcher
- **THEN** the app resumes its existing session rather than restarting from the earlier share

#### Scenario: Re-share replaces the source

- **WHEN** the app is running with a loaded source and the user shares a different video to Tagalong
- **THEN** the newly shared video becomes the working source at the Trim screen

### Requirement: A share without a usable video falls back silently to Home

When launched by a share intent that carries no usable video (missing, unreadable, or of an unsupported type), the app SHALL open at the Home screen as an ordinary launch and SHALL NOT display an error attributed to the share.

#### Scenario: Malformed share opens Home

- **WHEN** an app shares to Tagalong with no video payload the app can open
- **THEN** the app displays the Home screen with no error message

### Requirement: Shared videos from the system media provider are read unredacted when permitted

When the shared reference resolves to the device's system media provider and `ACCESS_MEDIA_LOCATION` is granted, the bytes the app materialises for cutting SHALL be the unredacted original, including any GPS location tags. Without the permission the flow SHALL proceed unchanged, with the framework's redaction applying as it does for in-app picks.

#### Scenario: Media-provider share with permission keeps location

- **WHEN** a video URI from the system media provider is shared to Tagalong
- **AND** `ACCESS_MEDIA_LOCATION` is granted
- **AND** the underlying video contains a GPS location tag
- **THEN** the materialised bytes contain that location tag

#### Scenario: Flow proceeds without the permission

- **WHEN** a video is shared to Tagalong while `ACCESS_MEDIA_LOCATION` is not granted
- **THEN** the trim and cut flow proceeds with no permission prompt and no blocking

### Requirement: Shared cuts preserve every tag present in the received byte stream

A cut started from a shared video SHALL run through the same metadata-preserving engine as an in-app pick: every container tag present in the received byte stream SHALL be preserved to the output unchanged, and the result screen's metadata diff SHALL compare against the received stream. The app SHALL make no guarantee that the received stream equals the sender's original file — redaction performed by the sending app before sharing is outside the cut contract — and SHALL show such sender-side absence honestly as absence on the source side of the diff, with no share-specific warning.

#### Scenario: Tags in the received stream survive the cut

- **WHEN** a cut is run on a shared video whose received bytes contain device, creation-time, and location tags
- **THEN** the cut output contains the same tags with identical values
- **AND** the result screen's metadata diff reports them as preserved

#### Scenario: Sender-side redaction is shown, not hidden

- **WHEN** the sending app handed over a byte stream from which it had already stripped a tag
- **THEN** the diff shows the tag as absent from the source
- **AND** no share-specific warning or permission prompt is displayed
