## MODIFIED Requirements

### Requirement: GPS location metadata is preserved through the pick-and-cut flow

The app SHALL declare and request the `ACCESS_MEDIA_LOCATION` runtime permission so that Android's media framework delivers an unredacted byte stream when the picked video is materialised to the local cache. With this permission granted, location tags in the source container are present in the bytes the cut engine reads and are copied to the output by the engine's normal metadata-copy path, with no manual tag injection.

The permission SHALL be requested only from the Home screen's media-location control (see the `home-screen` capability), never during the pick-and-cut flow. Permission state SHALL never block picking, trimming, or cutting. The Home screen's persistent media-location status display replaces the former one-time post-denial warning; the pick-and-cut flow SHALL show no permission warning of its own.

#### Scenario: Location tag is preserved when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **AND** the source video contains a GPS location tag in its container metadata
- **THEN** the cut output contains the same GPS location tag with an identical value

#### Scenario: Pick proceeds after permission is denied

- **WHEN** the user taps "Pick video" while `ACCESS_MEDIA_LOCATION` is not granted (never requested, or previously denied)
- **THEN** the file picker opens directly without any permission request
- **AND** the user can pick, trim, and cut without further interruption

#### Scenario: Warning is not shown when permission is granted

- **WHEN** the `ACCESS_MEDIA_LOCATION` permission has been granted
- **THEN** no location-warning message is displayed to the user in the pick-and-cut flow

#### Scenario: No permission warning in the pick-and-cut flow

- **WHEN** the user picks or cuts a video while `ACCESS_MEDIA_LOCATION` is not granted
- **THEN** the pick-and-cut flow displays no permission warning of its own
- **AND** the media-location status is conveyed only by the persistent Home screen display

#### Scenario: Cut succeeds without the permission

- **WHEN** the user completes a cut while `ACCESS_MEDIA_LOCATION` is not granted
- **THEN** the cut succeeds and all non-location metadata is preserved as normal
- **AND** the Home screen's persistent status continues to indicate that media location access is off
