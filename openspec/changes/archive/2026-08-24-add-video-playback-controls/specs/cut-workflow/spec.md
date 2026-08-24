## MODIFIED Requirements

### Requirement: User can preview the source and choose a trim range

The app SHALL present a preview of the selected video together with a trim range bounded by the clip's duration. The user SHALL be able to set a start point and an end point within that duration, and the app SHALL keep the preview and the chosen range consistent as the user adjusts them.

#### Scenario: Range is bounded by the clip

- **WHEN** a source video is selected
- **THEN** the trim range spans from the start of the clip to its full duration
- **AND** the start point cannot be set later than the end point

#### Scenario: Adjusting a trim handle updates the preview

- **WHEN** the user drags a trim handle to a new position
- **THEN** the preview shows the frame at that position
- **AND** if playback was active it SHALL be paused before the seek

## ADDED Requirements

### Requirement: User can play the video with audio

The app SHALL allow the user to play the selected video with audio in the preview area. The player SHALL provide transport controls including play, pause, and free seeking across the full clip duration. The app SHALL automatically stop playback when the playhead reaches the trim end point, regardless of whether the user started playing from within or beyond the trimmed region. When the trim end point changes while playback is active, the new end point SHALL become the stop boundary immediately.

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
