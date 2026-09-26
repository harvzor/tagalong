## MODIFIED Requirements

### Requirement: Transitions do not alter navigation outcomes

Adding transitions SHALL NOT change which screen is shown, the contents of the back stack, or the availability of navigation controls.

#### Scenario: Multi-level back is unchanged

- **WHEN** the user activates the Trim screen's back control
- **THEN** the app shows the Home screen
- **AND** the Trim screen is no longer on the back stack

#### Scenario: Function is not gated on animation

- **WHEN** the user activates "Cut and save" while a transition is in progress
- **THEN** the cut runs exactly as it would with no transition configured
