## Why

The cut engine currently preserves GPS coordinates only as normalized FFprobe tags. FFmpeg rewrites the source QuickTime `moov/udta/©xyz` location atom into generic `mdta/location` metadata, which FFprobe reports as equivalent but Google Photos does not recognize. The metadata viewer also hides this representation change, so it can report a location as preserved while the saved video no longer displays a location in a real gallery application. The README should explain this compatibility-sensitive metadata distinction so contributors understand why a logically present GPS tag is not sufficient.

## What Changes

- Update the cut-output metadata strategy to preserve a Google Photos-compatible QuickTime `©xyz` location atom in both lossless and re-encode modes.
- First evaluate and, if sufficient on the bundled Android FFmpeg build, adopt an FFmpeg muxer configuration that writes `©xyz` without dropping manufacturer-specific `com.*` metadata or changing `creation_time`.
- Retain a raw MP4 box-preservation/rewriting approach as the fallback when FFmpeg flags cannot preserve `©xyz` and the other metadata requirements together. The fallback SHALL preserve the source location representation rather than merely normalizing its coordinates.
- Extend metadata probing and the source/output metadata UI to distinguish logical location values from their physical container representation, including whether `moov/udta/©xyz` and generic `mdta` location entries are present.
- Update preservation assertions so a test cannot pass solely because FFprobe normalized incompatible container representations to the same `location` key.
- Add on-device regression coverage for the raw location atom, vendor tags, creation time, rotation, gallery date, and both cut modes. Validate the saved output against the Google Photos-compatible representation observed on the target device.
- Update the README's explanation of metadata preservation to describe the QuickTime `©xyz` location atom, the generic `mdta` alternative, and why both FFprobe and gallery-app compatibility must be considered.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metadata-preserving-cut`: require preservation of the source's consumer-relevant QuickTime location representation, not only a normalized logical location value, while retaining all existing metadata, orientation, and gallery-date guarantees.
- `probe-viewer`: expose the physical container representation of preservation-critical metadata and flag a location that is present to FFprobe but not present in the QuickTime `©xyz` form required by compatible gallery applications.
- `repository-readme`: explain the distinction between logical FFprobe metadata and the physical MP4 location atom required by compatible gallery applications.

## Impact

- `engine/src/main/java/dev/tagalong/engine/FfmpegCutEngine.kt` — evaluate and potentially change MP4 muxer flags or invoke the fallback metadata finalization path.
- `engine/src/main/java/dev/tagalong/engine/MetadataReader.kt` and `MediaProbe` — inspect and expose relevant MP4 box paths and metadata representations.
- `engine/src/androidTest` — add raw-container and device-level preservation assertions for every supported sample and cut mode.
- `app/src/main/java/dev/tagalong/app/ProbeCard.kt` and `MetadataDiffCard.kt` — display and compare logical values together with their container representation.
- `README.md` — document `©xyz` versus `mdta` location metadata and the compatibility implication.
- `app/src/androidTest` — extend end-to-end checks where practical and retain manual Google Photos verification for consumer compatibility.
- No new third-party dependency is required for the preferred FFmpeg-configuration path. A fallback raw MP4 box writer should be evaluated before introducing any new dependency.
