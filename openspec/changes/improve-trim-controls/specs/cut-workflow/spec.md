## ADDED Requirements

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
