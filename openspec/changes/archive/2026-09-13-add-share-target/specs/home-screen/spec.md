# home-screen (delta)

## MODIFIED Requirements

### Requirement: Home screen is the launch destination

The app SHALL open to the Home screen on a launcher launch. The Home screen SHALL NOT require any permissions or video to be pre-selected. A launch that carries a usable shared video is not a launcher launch and SHALL instead open at the Trim screen (see the `share-intake` capability); a share whose video is unusable falls back to this requirement's ordinary Home launch.

#### Scenario: Cold launch

- **WHEN** the user opens the app for the first time
- **THEN** the Home screen is displayed with a "Pick video" button

#### Scenario: Launcher launch after a share session

- **WHEN** the user reopens Tagalong from the launcher after a session that began as a share
- **THEN** the app resumes its existing session, or opens the Home screen if that session has ended
- **AND** the earlier share is not replayed

### Requirement: Back navigation from Trim returns to Home

When the user navigates back from a Trim screen whose session began at the Home screen (an in-app pick), the app SHALL return to the Home screen rather than exiting. When the user navigates back from a Trim screen that was opened directly by a shared video, the app SHALL exit to the sending app rather than showing the Home screen, because the user's point of entry was the sender, not Home.

#### Scenario: Back from Trim

- **WHEN** the user presses back on a Trim screen reached via "Pick video" from the Home screen
- **THEN** the app displays the Home screen

#### Scenario: Back from Trim after a share

- **WHEN** the user pressed back on a Trim screen opened by sharing a video from another app
- **THEN** the app exits and the sending app is shown
- **AND** the Home screen is not displayed
