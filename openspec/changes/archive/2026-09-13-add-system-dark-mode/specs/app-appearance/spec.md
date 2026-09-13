## Purpose

Decides whether the app presents in a light or dark appearance, keeping that decision aligned
with the device's system night mode, and keeping the surfaces outside the app's own drawn
content — status bar icon contrast and the window background shown before the app paints its
first frame — consistent with it.

## ADDED Requirements

### Requirement: Appearance follows the system night mode

The app SHALL present in a dark appearance when the device is in night mode and in a light
appearance when it is not. The app SHALL NOT offer a control that selects an appearance, and
SHALL NOT override the device's night mode.

#### Scenario: Launch on a device in night mode

- **WHEN** the device is set to dark mode and the user opens the app
- **THEN** the app presents a dark appearance

#### Scenario: Launch on a device not in night mode

- **WHEN** the device is set to light mode and the user opens the app
- **THEN** the app presents a light appearance

#### Scenario: No appearance control is offered

- **WHEN** the user browses every screen of the app
- **THEN** no control to force light or dark appearance is present

### Requirement: Every screen uses the active appearance

Every screen the app displays SHALL use the active appearance, including text, control,
background, and state-indicating colours. No screen SHALL be exempt from it.

#### Scenario: Trimming in night mode

- **WHEN** the device is in night mode and the user picks a video and reaches the trimming screen
- **THEN** the trimming screen is presented in the dark appearance, including its labels and
  controls

#### Scenario: Result and About in night mode

- **WHEN** the device is in night mode and the user reaches the result screen or the About screen
- **THEN** each screen is presented in the dark appearance

#### Scenario: Readability is preserved in both appearances

- **WHEN** any screen is displayed in either appearance
- **THEN** its text and controls are legible against the surface they sit on

### Requirement: Appearance tracks a change to the system setting

When the device's night mode changes, the app SHALL adopt the new appearance without requiring
the user to restart it. Any cut in progress, and any video or trim state the user has established,
SHALL be unaffected by that change.

#### Scenario: Night mode changes while the app is open

- **WHEN** the user changes the device night mode while the app is visible
- **THEN** the screen the user is on is presented in the new appearance

#### Scenario: Night mode changes while the app is backgrounded

- **WHEN** the user changes the device night mode with the app in the background and then returns
  to it
- **THEN** the app is presented in the new appearance without the user closing and reopening it

#### Scenario: Returning after a mid-trim appearance change

- **WHEN** the device night mode changes while the user has a video loaded on the trimming screen
  and the user returns to the app
- **THEN** the same video is still loaded with the same trim range
- **AND** the preview still plays

### Requirement: Non-content surfaces match the active appearance

The surfaces the app is responsible for but does not draw as content — the contrast of the status
bar icons over the app's background, and the window background shown before the app paints its
first frame — SHALL match the active appearance.

#### Scenario: Status area stays legible in the dark appearance

- **WHEN** the app is displayed in the dark appearance
- **THEN** the system status area's icons and clock are legible against the app's background

#### Scenario: Cold start shows no opposite-appearance frame

- **WHEN** the device is in night mode and the user opens the app from cold
- **THEN** the background shown while the app starts is the dark appearance's background, not the
  light one
