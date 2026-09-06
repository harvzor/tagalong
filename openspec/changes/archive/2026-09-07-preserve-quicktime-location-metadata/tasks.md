## 1. Establish raw MP4 representation probing

- [x] 1.1 Add a focused MP4 box walker in the engine layer that can identify `moov/udta/©xyz` and `meta`/`mdta` location entries, including 32-bit and extended-size boxes.
- [x] 1.2 Add unit-level or deterministic fixture tests for the two canonical samples, asserting that both `google-pixel-10a.mp4` and `xiaomi-poco-x5.mp4` contain `moov/udta/©xyz` and that their source payloads are read without coordinate normalization.
- [x] 1.3 Extend `MediaProbe` and `MetadataReader` with location-representation information while retaining the existing normalized FFprobe format and stream tag maps.

## 2. Evaluate the FFmpeg-only solution first

- [x] 2.1 Add a diagnostic execution path or instrumented test that compares the current muxer flags, a configuration without `use_metadata_tags`, and any explicit location-metadata variant needed to request QuickTime location output.
- [x] 2.2 Run the diagnostic matrix on the bundled Android FFmpeg build for both samples and both cut modes, recording raw `©xyz` presence/payload, vendor tags, `creation_time`, rotation, gallery date, and source immutability.
- [x] 2.3 If a flag-only configuration satisfies the complete matrix, update `FfmpegCutEngine` to use it in both modes and remove the diagnostic-only path. (Not applicable: all Android matrix variants lost `©xyz` or vendor tags, so the fallback is enabled.)

## 3. Conditional raw-box preservation fallback

- [x] 3.1 If no FFmpeg-only configuration satisfies the matrix, implement an MP4 metadata finalizer that copies the source `©xyz` box payload into the output while retaining generic vendor metadata.
- [x] 3.2 Make the finalizer correctly update enclosing MP4 box sizes and `stco`/`co64` offsets, and reject unsupported layouts with a surfaced failure rather than emitting uncertain output.
- [x] 3.3 Add lossless and re-encode tests proving that the finalizer preserves media-packet behavior appropriate to each mode, source tags, exact `©xyz` payload, and source immutability.

## 4. Expose physical metadata representation in the UI

- [x] 4.1 Update `ProbeCard` to show the logical location value together with its representation status: QuickTime `©xyz`, generic `mdta`, both, or absent.
- [x] 4.2 Update `MetadataDiffCard` to compare representation status and mark a change from `©xyz` to only generic `mdta` as a preservation-critical change.
- [x] 4.3 Ensure the viewer distinguishes an absent location from a location that FFprobe reports logically but whose required QuickTime representation is missing.

## 5. Strengthen automated verification

- [x] 5.1 Update engine contract assertions to compare raw location representation and payload in addition to normalized format tags for every sample and both cut modes.
- [x] 5.2 Extend app end-to-end verification to assert that the saved output retains the required representation after the pick, cut, MediaStore registration, and cache-to-gallery copy path.
- [x] 5.3 Retain existing assertions for vendor tags, `creation_time`, orientation, gallery date, codec/dimensions, and source bytes; do not weaken the known re-encode rotation failure.
- [x] 5.4 Manually verify the source and saved output in Google Photos on the connected device, recording the result as release evidence rather than making the test depend on Google Photos UI internals.

## 6. Documentation and validation

- [x] 6.1 Update the README "How It Works" section to explain `moov/udta/©xyz`, generic `mdta/location`, FFprobe normalization, and why the QuickTime representation matters to gallery compatibility.
- [x] 6.2 Mention that both canonical device samples contain the QuickTime location atom without exposing their GPS coordinates in repository documentation.
- [x] 6.3 Run `openspec validate --change "preserve-quicktime-location-metadata"` and the relevant Gradle build and instrumented test suites, documenting any pre-existing re-encode rotation-gap failures. (Validation/builds pass; lossless/parser/diagnostic tests pass; re-encode remains failing only on the documented rotation signal gap; app E2E was blocked by duplicate stale picker cards.)
