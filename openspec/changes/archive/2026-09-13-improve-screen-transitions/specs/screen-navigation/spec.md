## Purpose

Governs how the app's screens animate when the user moves between them, so that navigation reads as intentional movement with a clear direction, and so that two screens are never visible through one another mid-transition.

## ADDED Requirements

### Requirement: Screen transitions convey direction

Navigating forward to a screen SHALL animate the incoming screen in from the right edge while the outgoing screen moves out to the left. Returning to a screen SHALL animate the reverse: the departing screen moves out to the right and the returning screen moves in from the left. The two screens SHALL move at different rates so the motion reads as depth rather than as one image being dragged.

#### Scenario: Forward navigation slides in from the right

- **WHEN** the user navigates from the Home screen to the Trim screen
- **THEN** the Trim screen enters from the right edge
- **AND** the Home screen moves out to the left at a slower rate than the Trim screen enters

#### Scenario: Back navigation reverses the direction

- **WHEN** the user returns from the Trim screen to the Home screen
- **THEN** the Trim screen exits to the right
- **AND** the Home screen enters from the left

#### Scenario: About behaves as a peer screen

- **WHEN** the user opens the About screen from Home
- **THEN** the About screen enters from the right edge, in the same manner as any other forward navigation
- **AND** returning from About reverses that motion

### Requirement: Transitions are brisk and continuous

Screen transitions SHALL complete within 400 ms of the navigation being requested and SHALL NOT present the two screens as a static crossfade in which neither screen moves.

#### Scenario: Transition duration

- **WHEN** the user navigates between any two screens
- **THEN** the animation completes within 400 ms

#### Scenario: Motion is visible

- **WHEN** the user navigates between two screens with system animations enabled
- **THEN** at least one of the two screens is visibly translating across the screen during the transition

### Requirement: Each screen presents an opaque surface

Every screen SHALL draw an opaque background across its full area, so that where two screens overlap during a transition the screen behind is not visible through the screen in front.

#### Scenario: Overlap during forward navigation

- **WHEN** the incoming screen partially overlaps the outgoing screen during a forward transition
- **THEN** the content of the outgoing screen is not visible through the incoming screen

#### Scenario: Returning screen is composited beneath

- **WHEN** the user returns to a screen that is being revealed from beneath a departing screen
- **THEN** the content of the departing screen is not visible through the returning screen's background

#### Scenario: App appearance is unchanged at rest

- **WHEN** any screen is displayed and no transition is in progress
- **THEN** the screen looks the same as it did before this capability was introduced

### Requirement: Reduced motion is respected

When the system animation scale is disabled, screen navigation SHALL NOT play a travel animation; the destination screen SHALL appear without visible movement.

#### Scenario: Animations are switched off in system settings

- **WHEN** the system animation scale is set to off and the user navigates between screens
- **THEN** the destination screen appears without sliding

### Requirement: Transitions do not alter navigation outcomes

Adding transitions SHALL NOT change which screen is shown, the contents of the back stack, or the availability of navigation controls.

#### Scenario: Multi-level back is unchanged

- **WHEN** the user activates "Pick a different video" on the Trim screen
- **THEN** the app shows the Home screen
- **AND** the Trim and Result screens are no longer on the back stack

#### Scenario: Function is not gated on animation

- **WHEN** the user activates "Cut and save" while a transition is in progress
- **THEN** the cut runs exactly as it would with no transition configured
