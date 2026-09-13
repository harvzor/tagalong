## ADDED Requirements

### Requirement: Home screen displays media location access status
The Home screen SHALL display the current media location (`ACCESS_MEDIA_LOCATION`) permission status at all times, positioned above the "Pick video" control. When the permission is not granted, the Home screen SHALL explain the effect in terms of the file contents (location data stored inside videos is stripped from cuts without the permission) and SHALL provide a control to enable it. When the permission is granted, the Home screen SHALL display a granted indicator. The displayed status SHALL reflect the current OS permission state, including changes made from system settings while the app was backgrounded.

#### Scenario: Status shown when permission is not granted
- **WHEN** the Home screen is displayed and `ACCESS_MEDIA_LOCATION` is not granted
- **THEN** a media-location block is shown above the "Pick video" button
- **AND** the block explains that GPS data inside videos will be lost from cuts without the permission
- **AND** the block contains an "Enable media location access" control

#### Scenario: Status shown when permission is granted
- **WHEN** the Home screen is displayed and `ACCESS_MEDIA_LOCATION` is granted
- **THEN** a granted indicator for media location access is shown
- **AND** no enable control or warning is shown

#### Scenario: Status reflects a system settings change
- **WHEN** the user changes the media location permission in system settings and returns to the Home screen
- **THEN** the displayed status matches the new permission state without requiring an app restart

### Requirement: Media location access is requested only from the Home screen
The runtime permission request for `ACCESS_MEDIA_LOCATION` SHALL be triggered only by the Home screen's enable control. Picking a video, trimming, and cutting SHALL never trigger a permission request and SHALL never be blocked by the permission state; the "Pick video" control SHALL always be enabled. When the OS no longer shows its permission dialog for a pending request, activating the enable control SHALL instead open the app's system settings page so the user can grant the permission there.

#### Scenario: Pick flow never prompts for permission
- **WHEN** the user taps "Pick video" and `ACCESS_MEDIA_LOCATION` is not granted
- **THEN** the system file picker opens directly with no permission dialog
- **AND** the picked video proceeds through trim and cut normally

#### Scenario: Enable control requests the permission
- **WHEN** the user activates "Enable media location access" and the OS can show a permission dialog
- **THEN** the system permission dialog for media location access is shown

#### Scenario: Enable control falls back to app settings when the dialog is suppressed
- **WHEN** the user has already declined the permission such that the OS silently auto-denies further requests
- **AND** the user activates "Enable media location access"
- **THEN** the app opens its system app-details settings page instead of issuing a request whose dialog would never appear
