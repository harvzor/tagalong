## Purpose

Controls how the app identifies itself on system surfaces outside the app's own
drawn content — the launcher label shown in the app drawer, on the home screen,
in the recents list, and in the system Settings app entry, and the icon rendered
alongside it — and how that identity relates to the brand shown inside the app.

## ADDED Requirements

### Requirement: Launcher icon is the Tagalong mark

The application icon presented on all system surfaces SHALL be the Tagalong
adaptive launcher icon. The icon SHALL be declared on the `<application>` element
so that every surface resolves the same drawable, and SHALL NOT fall back to the
platform default application icon.

#### Scenario: App appears with its own icon in the drawer

- **WHEN** the user installs the app and views it in the app drawer, or adds it
  to the home screen
- **THEN** the tile renders the Tagalong mark, not the platform default
  application icon

#### Scenario: System surfaces render the same icon

- **WHEN** the user views the app in the recents/task switcher or in the
  system Settings → Apps list
- **THEN** each surface renders the Tagalong mark

#### Scenario: The package declares an application icon

- **WHEN** the built debug APK is inspected with
  `aapt2 dump badging <apk>`
- **THEN** the report lists a non-empty `application-icon` entry and an
  adaptive-icon declaration, rather than `icon=''`

### Requirement: Icon artwork survives every launcher mask

The adaptive icon foreground SHALL be sized so that its artwork remains inside
the region an adaptive icon mask reveals. Foreground artwork SHALL NOT be
dependent on the outer region of the 108dp adaptive canvas, which launchers
are permitted to clip.

#### Scenario: Artwork is complete under the tightest available mask

- **WHEN** the icon is rendered by a launcher using each icon shape that
  launcher offers
- **THEN** no part of the hashtag or film reel is cut off by the mask

#### Scenario: Artwork is complete under the square mask

- **WHEN** the icon is rendered under a square mask, which reveals the largest
  region of the adaptive canvas
- **THEN** the artwork is composed as intended and reads as the Tagalong mark

#### Scenario: The mark stays legible at launcher size

- **WHEN** the icon is rendered at app-drawer size
- **THEN** the hashtag silhouette and the film reel remain distinguishable

### Requirement: No monochrome layer is shipped

The adaptive icon SHALL NOT declare a monochrome layer. A monochrome layer is a
contract that its artwork is single-colour, designed to be masked by its own
alpha and flooded with the system accent colour; the Tagalong mark is multi-colour
artwork with dark outlines and does not satisfy that contract. Themed-icon
launchers SHALL therefore render the system's derived treatment of the standard
icon. The deliberate absence of this layer SHALL be recorded beside the
`android:icon` declaration, because regenerating the asset set through Android
Studio's Image Asset re-adds a monochrome layer pointing at the colour foreground.

#### Scenario: Adaptive icon declares two layers

- **WHEN** either adaptive icon resource under
  `app/src/main/res/mipmap-anydpi-v26/` is read
- **THEN** it declares a `background` and a `foreground`, and declares no
  `monochrome`

#### Scenario: Themed-icon mode is not falsely claimed

- **WHEN** the device is set to a themed-icon mode
- **THEN** the app advertises no monochrome layer, and the launcher applies its
  own treatment to the standard icon

## MODIFIED Requirements

### Requirement: Functionally significant identifiers are unaffected

The label and icon changes SHALL NOT alter the application id, the output video
location, the picker or share intake behaviour, the cut engine, metadata
preservation, or any resource/theme identifier the app or its tests rely on.

#### Scenario: Cuts still save to the same gallery location

- **WHEN** the user completes a cut after this change
- **THEN** the output is saved under `Movies/Tagalong/` as before

#### Scenario: App identity is unchanged for updates

- **WHEN** this version is installed over a previous version
- **THEN** it is treated as an update to the same app (same application id
  `dev.tagalong.app`), not as a separate app

#### Scenario: Existing suites are unaffected

- **WHEN** the engine and app instrumented suites run after this change
- **THEN** they pass unchanged, having exercised no icon code path
