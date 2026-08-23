## MODIFIED Requirements

### Requirement: Output metadata shown after cut

After a successful cut, the app SHALL display the cut output's metadata as part of the unified diff card on the result screen. The diff card SHALL appear on the result screen only — not on the trim screen. No separate output-only metadata card SHALL be shown anywhere in the app.

#### Scenario: Output metadata appears on result screen after cut

- **WHEN** a cut completes successfully and the user is on the result screen
- **THEN** the metadata diff card shows cut output values alongside source values for every curated tag

#### Scenario: Output metadata not shown on trim screen

- **WHEN** a cut completes successfully and the user navigates back to the trim screen
- **THEN** no output metadata card is shown on the trim screen

#### Scenario: Output card not shown before cut

- **WHEN** a video is picked but no cut has been performed
- **THEN** only the source metadata card is visible on the trim screen; no output metadata is shown anywhere
