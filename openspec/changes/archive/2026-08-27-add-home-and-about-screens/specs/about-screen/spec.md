## Purpose

The About screen displays the app's identity and satisfies the LGPL v3 §4(a) requirement to give prominent notice that ffmpeg-kit is used, by naming the library, its author, its license, and linking to its source.

## ADDED Requirements

### Requirement: About screen shows app identity
The About screen SHALL display the app name and current version number.

#### Scenario: App identity visible
- **WHEN** the user opens the About screen
- **THEN** the app name "Tagalong" and the current version (e.g. "v1.0") are visible

### Requirement: About screen attributes ffmpeg-kit-full-gpl
The About screen SHALL prominently display that the app uses ffmpeg-kit-full-gpl, including the library name, version, author (Anton Karpenko), license (GPL v3 / LGPL v3), and a tappable link to the source repository.

#### Scenario: Attribution visible
- **WHEN** the user opens the About screen
- **THEN** the text "ffmpeg-kit-full-gpl 2.1.0 by Anton Karpenko, GPL v3 / LGPL v3" or equivalent is visible

#### Scenario: Source link is tappable
- **WHEN** the user taps the source link for ffmpeg-kit-full-gpl
- **THEN** the device browser opens to https://github.com/sk3llo/ffmpeg-kit-flutter

### Requirement: About screen states the app's own license
The About screen SHALL state that Tagalong itself is licensed under GPL v3.

#### Scenario: App license visible
- **WHEN** the user opens the About screen
- **THEN** "GPL v3" or "GNU General Public License v3" is visible as the app's license

### Requirement: Back navigation from About returns to Home
Pressing back on the About screen SHALL return the user to the Home screen.

#### Scenario: Back from About
- **WHEN** the user presses back on the About screen
- **THEN** the app displays the Home screen
