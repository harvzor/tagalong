## MODIFIED Requirements

### Requirement: GPS location metadata is preserved through the pick-and-cut flow

The app SHALL declare and request the `ACCESS_MEDIA_LOCATION` runtime permission so that Android's media framework delivers an unredacted byte stream when the picked video is materialised to the local cache. With this permission granted, location tags in the source container are present in the bytes the cut engine reads and are copied to the output by the engine's normal metadata-copy path, with no manual tag injection.

The permission SHALL be requested before the file picker is launched. If the user denies the permission, the app SHALL still allow picking and cutting; the user SHALL be shown a one-time warning that GPS location metadata may not appear in the cut output. The warning SHALL be shown only when the permission was denied, not on every pick.

#### Scenario: Location tag is preserved when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **AND** the source video contains a GPS location tag in its container metadata
- **THEN** the cut output contains the same GPS location tag with an identical value

#### Scenario: Pick proceeds after permission is denied

- **WHEN** the user denies the `ACCESS_MEDIA_LOCATION` permission request
- **THEN** the app continues to the file picker without blocking the flow
- **AND** the app shows a one-time warning that GPS location may not be preserved in the output

#### Scenario: Warning is not shown when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **THEN** no location-warning message is displayed to the user
