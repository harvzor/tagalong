## Purpose

Presents the result of a successful cut as a dedicated screen where the user can preview the cut video, confirm its storage path, and verify that every metadata tag survived the cut unchanged.

## Requirements

### Requirement: App navigates to the result screen after a successful cut

After a cut completes successfully the app SHALL navigate forward to the result screen. The trim screen SHALL remain on the back stack so the user can return to it.

#### Scenario: Navigation occurs on cut success

- **WHEN** a cut completes successfully
- **THEN** the app navigates to the result screen
- **AND** the trim screen is preserved on the back stack

#### Scenario: Navigation does not occur on cut failure

- **WHEN** a cut fails
- **THEN** the app remains on the trim screen and displays the error
- **AND** the result screen is not shown

### Requirement: Result screen displays the cut video for playback

The result screen SHALL show a playable preview of the cut output so the user can confirm the clip before leaving the screen.

#### Scenario: Cut video is playable on the result screen

- **WHEN** the result screen is shown
- **THEN** a video player displaying the cut output is visible and playable

### Requirement: Result screen displays the full absolute path of the cut output

The result screen SHALL display the absolute storage path of the saved cut file (e.g. `/storage/emulated/0/DCIM/Camera/video_cut.mp4`) so the user knows exactly where the file was saved.

#### Scenario: Output path is shown

- **WHEN** the result screen is shown
- **THEN** the full absolute path of the cut output file is displayed

### Requirement: Result screen shows a metadata diff comparing source and cut output

The result screen SHALL display a single card that presents every curated metadata tag in two parallel columns — source value and cut output value — so the user can verify preservation at a glance. The same set of curated tags defined by the `probe-viewer` capability SHALL be shown.

#### Scenario: Curated tags appear in two columns

- **WHEN** the result screen is shown
- **THEN** each curated tag row shows the source value and the cut output value side by side

#### Scenario: Overflow tags are accessible via expand control

- **WHEN** the user taps the expand control on the diff card
- **THEN** all remaining tag key-value pairs (not shown in the curated summary) are revealed with both source and output values

### Requirement: Metadata diff card shows a summary banner

At the top of the metadata diff card the app SHALL show a summary banner indicating whether all tags were preserved or whether any tags are missing or changed.

#### Scenario: All tags preserved — banner is affirmative

- **WHEN** every curated tag present in the source is also present with the same value in the cut output
- **THEN** the banner reads "All N tags preserved" and is styled to indicate success

#### Scenario: Tags missing or changed — banner is a warning

- **WHEN** any curated tag is absent from the cut output or has a different value
- **THEN** the banner indicates the number of affected tags and is styled as a warning

### Requirement: Rows with missing or changed values are visually distinguished

Any tag row where the cut output value differs from the source value, or is absent, SHALL be visually distinguished from rows where the value is identical, so the user can spot problems without reading every row.

#### Scenario: Changed or missing row is highlighted

- **WHEN** a tag's cut output value differs from its source value or is absent
- **THEN** that row is rendered in a way that distinguishes it from preserved rows (e.g. different text colour or a status indicator)

#### Scenario: Preserved rows are rendered neutrally

- **WHEN** a tag's cut output value is identical to its source value
- **THEN** the row is rendered without additional visual emphasis

### Requirement: Back navigation returns to the trim screen with trim state intact

The result screen SHALL provide a back action that returns the user to the trim screen. The trim screen SHALL be in the same state it was in when the cut was started — trim handles at the same positions, source video loaded — so the user can adjust and re-cut if desired.

#### Scenario: Back returns to trim screen

- **WHEN** the user activates the back action from the result screen
- **THEN** the app navigates back to the trim screen

#### Scenario: Trim state is preserved across round-trip

- **WHEN** the user returns to the trim screen from the result screen
- **THEN** the trim handles are at the same positions they were at when the cut was started
- **AND** the source video is still loaded
