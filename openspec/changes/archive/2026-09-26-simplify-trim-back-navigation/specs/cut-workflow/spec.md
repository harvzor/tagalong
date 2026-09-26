## MODIFIED Requirements

### Requirement: User can play the video with audio

The app SHALL allow the user to play the selected video with audio in the preview area. The player SHALL provide transport controls including play, pause, and free seeking across the full clip duration. The app SHALL automatically stop playback when the playhead reaches the trim end point, regardless of whether the user started playing from within or beyond the trimmed region. When the trim end point changes while playback is active, the new end point SHALL become the stop boundary immediately. Playback SHALL also stop when the user leaves the Trim screen, and it SHALL stop before the screen transition to the destination begins rather than when the Trim screen's lifecycle is later downgraded.

#### Scenario: Video plays with audio from current position

- **WHEN** the user activates play
- **THEN** the video plays in real-time with audio from the current playhead position

#### Scenario: Playback stops at trim end point

- **WHEN** playback is active and the playhead reaches the trim end time
- **THEN** playback pauses automatically

#### Scenario: Seeking past trim end then playing still stops at trim end

- **WHEN** the user seeks the playhead to a position beyond the trim end time and then activates play
- **THEN** playback pauses automatically when the playhead reaches the trim end time

#### Scenario: Adjusting trim end during playback updates stop boundary

- **WHEN** the user adjusts the trim end handle while playback is active
- **THEN** the new trim end time becomes the stop boundary immediately

#### Scenario: Playback stops when leaving the Trim screen by the back arrow

- **WHEN** playback is active and the user activates the Trim screen's back control
- **THEN** playback stops
- **AND** the app shows the Home screen

#### Scenario: Playback stops when picking a different video

- **WHEN** playback is active and the user leaves the Trim screen in order to pick a different video (the Trim screen's single back control)
- **THEN** playback stops before the Home screen begins to appear

#### Scenario: Playback stops when leaving the Trim screen by the system back gesture

- **WHEN** playback is active and the user triggers the system back gesture or hardware back while the Trim screen is shown
- **THEN** playback stops before the destination screen begins to appear

#### Scenario: Playback stops when the cut is saved

- **WHEN** playback is active and the cut completes successfully, navigating to the Result screen
- **THEN** playback on the Trim screen's preview stops
