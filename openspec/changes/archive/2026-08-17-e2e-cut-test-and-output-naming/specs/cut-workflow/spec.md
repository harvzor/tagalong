## ADDED Requirements

### Requirement: The saved output is named for its source and trim bounds

The app SHALL derive the saved output's display name from the source video's base name and the start and end timestamps of the cut, using the format `{originalBaseName}_from_{HH-MM-SS-mmm}_to_{HH-MM-SS-mmm}.mp4`. Time values SHALL use `-` as the only separator (colon-free, file-safe on all platforms). Millisecond precision SHALL be included as a 3-digit value following the seconds field. The source extension is stripped from the base name; the output extension is always `.mp4`.

#### Scenario: Display name encodes source and bounds

- **WHEN** the user saves a cut of `xiaomi-poco-x5.mp4` from 500 ms to 3 500 ms
- **THEN** the saved file's display name is `xiaomi-poco-x5_from_00-00-00-500_to_00-00-03-500.mp4`

#### Scenario: Display name for a cut starting beyond one minute

- **WHEN** the user saves a cut that starts at 1 h 22 min 10.750 s and ends at 1 h 25 min 44.000 s
- **THEN** the saved file's display name is `{originalBaseName}_from_01-22-10-750_to_01-25-44-000.mp4`
