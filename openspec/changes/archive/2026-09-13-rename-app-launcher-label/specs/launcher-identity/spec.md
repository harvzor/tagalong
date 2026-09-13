## Purpose

Controls how the app identifies itself on system surfaces outside the app's own
drawn content — the launcher label shown in the app drawer, on the home screen,
in the recents list, and in the system Settings app entry — and how that label
relates to the brand shown inside the app.

## ADDED Requirements

### Requirement: Launcher label identifies the app and its function

The application label presented on all system surfaces SHALL be
`Tagalong: Video Cutter`. A single string resource SHALL be the source of the
label so that every system surface renders the same text.

#### Scenario: App appears in the drawer and on the home screen

- **WHEN** the user installs the app and views it in the app drawer, or adds it
  to the home screen
- **THEN** the caption under the icon reads `Tagalong: Video Cutter`

#### Scenario: System surfaces render the same label

- **WHEN** the user views the app in the recents/task switcher or in the
  system Settings → Apps list
- **THEN** each surface renders the label `Tagalong: Video Cutter`

### Requirement: In-app brand remains the short name

The name rendered inside the app's own screens SHALL remain `Tagalong`. The
launcher label change SHALL NOT alter any text drawn by the app itself.

#### Scenario: Home screen wordmark unchanged

- **WHEN** the user opens the app
- **THEN** the Home screen brand text reads `Tagalong`

#### Scenario: About screen unchanged

- **WHEN** the user opens the About screen
- **THEN** the app name shown there reads `Tagalong`

### Requirement: Functionally significant identifiers are unaffected

The label change SHALL NOT alter the application id, the output video location,
or any resource/theme identifier the app or its tests rely on.

#### Scenario: Cuts still save to the same gallery location

- **WHEN** the user completes a cut after this change
- **THEN** the output is saved under `Movies/Tagalong/` as before

#### Scenario: App identity is unchanged for updates

- **WHEN** this version is installed over a previous version
- **THEN** it is treated as an update to the same app (same application id
  `dev.tagalong.app`), not as a separate app
