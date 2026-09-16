## Why

Tagalong ships the green Android placeholder. The full Image Asset export is in the
tree — every density, adaptive foreground, legacy raster, round raster, background
colour, and the 512×512 Play Store asset — but nothing references it. There is no
`android:icon` on `<application>`, so all 19 resource files are orphans and every
system surface renders the platform default.

This is measured, not inferred. `aapt2 dump badging` on the last-built debug APK
reports `application: label='Tagalong: Video Cutter' icon=''` and
`launchable-activity: label='' icon=''`.

The `launcher-identity` capability already specifies how the app names itself on
those surfaces. It has never specified how it *looks*, which is the other half of
the same contract.

## What Changes

- Wire `android:icon` and `android:roundIcon` on `<application>` so the exported
  resources are actually used — the two lines that make everything else visible.
- Remove the `<monochrome>` layer from both adaptive-icon XMLs. Image Asset emitted
  one pointing at the **colour** foreground, but the monochrome slot is a
  contract: themed-icon launchers take that layer's alpha and flood it with the
  system accent. Five-colour artwork with heavy dark outlines becomes a
  solid accent-coloured blob, where the film reel is swallowed by the hashtag.
  Shipping no monochrome layer is a better outcome than shipping a fake one.
- Foreground artwork scaled from 60% to 55% of the adaptive canvas. Adaptive masks
  reveal only the central 66dp of the 108dp canvas — roughly 61% of the artwork.
  At 60% the hashtag arms ran to the mask edge and clipped on tighter launchers.
  55% is the setting verified on-device: still inside the Pixel circle mask, with
  the reel holes and filmstrip legible at drawer size.
- Record the deliberate absence of a monochrome layer as a comment beside the
  manifest `icon` attribute. Image Asset re-adds the layer whenever its Monochrome
  page is populated, so without a recorded reason this regresses silently.

Not in scope: a `values-night` variant of `ic_launcher_background`, the Play Store
512 composition, and an Android 12+ splash-screen theme. See Design.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `launcher-identity`: currently specifies only the launcher **label**. Adds the
  icon half of the same contract — the mark rendered on launcher and system
  surfaces, artwork that survives every launcher mask, and the deliberate absence
  of a monochrome layer.

## Impact

- **Manifest**: `app/src/main/AndroidManifest.xml` — two attributes on
  `<application>`, plus a rationale comment.
- **Resources**: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and
  `ic_launcher_round.xml` — `<monochrome>` element removed from each. Already
  present in the working tree: the regenerated 50% foreground set, and
  `ic_launcher-playstore.png`.
- **Tests**: new `app/src/androidTest` coverage for launcher identity, which today
  has none — all three existing `launcher-identity` scenarios are manual.
- **No behavioural surface**: no change to the cut engine, metadata preservation,
  picker, share intake, navigation, or appearance. No new dependency. No
  permission change. `applicationId` stays `dev.tagalong.app` — installs as an
  update, not a new app.
- **Known lint effect**: removing `<monochrome>` surfaces AGP's
  `MonochromeLauncherIcon` warning. Cosmetic. There is no `lint {}` block, no
  baseline file, and the release Dockerfile runs only `assembleRelease` — no lint
  step. Suppression, not re-addition, is the response if it becomes noise.
