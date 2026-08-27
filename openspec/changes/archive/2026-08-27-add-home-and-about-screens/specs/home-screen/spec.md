## Purpose

The Home screen is the app's entry point, giving users a single place to pick a video to trim and navigate to the About screen.

## ADDED Requirements

### Requirement: Home screen is the launch destination
The app SHALL open to the Home screen on launch. The Home screen SHALL NOT require any permissions or video to be pre-selected.

#### Scenario: Cold launch
- **WHEN** the user opens the app for the first time
- **THEN** the Home screen is displayed with a "Pick video" button

### Requirement: User can pick a video from Home
The Home screen SHALL provide a control to open the system file picker filtered to video files. On a successful pick the app SHALL navigate to the Trim screen.

#### Scenario: Successful pick
- **WHEN** the user taps "Pick video" and selects a video in the system picker
- **THEN** the app navigates to the Trim screen with that video loaded

#### Scenario: Picker dismissed without selection
- **WHEN** the user taps "Pick video" and dismisses the picker without selecting a file
- **THEN** the app remains on the Home screen

### Requirement: User can reach the About screen from Home
The Home screen SHALL provide a navigation control that takes the user to the About screen.

#### Scenario: Navigating to About
- **WHEN** the user activates the About control on the Home screen
- **THEN** the app navigates to the About screen

### Requirement: Back navigation from Trim returns to Home
When the user navigates back from the Trim screen, the app SHALL return to the Home screen rather than exiting.

#### Scenario: Back from Trim
- **WHEN** the user presses back on the Trim screen
- **THEN** the app displays the Home screen
