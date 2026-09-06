## Context

See proposal.md for the motivation and the modified capability contracts. Both canonical device samples currently contain a QuickTime `moov/udta/©xyz` location atom: the Pixel 10a stores `+52.5562+13.3418/`, and the Xiaomi sample stores `+52.5182+013.4064/`. The current FFmpeg command uses `+faststart+use_metadata_tags`; the resulting output retains a logical FFprobe `location` value but moves the location into generic `mdta` metadata. The high-level FFprobe API used by `MetadataReader` flattens both forms into the same map key, and the current UI therefore cannot expose the representation change.

The implementation must remain compatible with the production Android FFmpeg build (`ffmpeg-kit-full-gpl:2.1.0`, bundled FFmpeg 8.1.1), not only a host FFmpeg version. The existing re-encode rotation-signal gap remains a known issue; this change must not weaken its existing assertions or claim to solve it unless a selected metadata finalization approach solves it as a direct consequence.

## Goals / Non-Goals

**Goals:**

- Preserve the source QuickTime `©xyz` location representation and payload in lossless and re-encode outputs.
- First determine whether an FFmpeg muxer configuration can achieve that while retaining vendor tags and `creation_time`.
- Keep a raw MP4 box-preservation path available when FFmpeg configuration alone cannot satisfy the contract.
- Make the metadata viewer show both normalized values and compatibility-relevant container representation.
- Make tests detect a `location` value that survived only because FFprobe normalized an incompatible representation.
- Document the distinction in the repository README.

**Non-Goals:**

- Replacing FFmpeg with Media3 Transformer.
- Changing the picker, input permission model, or gallery-date contract.
- Treating MediaStore latitude/longitude columns as a substitute for embedded file metadata.
- Weakening the existing metadata, orientation, or source-immutability assertions to accommodate a tool limitation.
- Expanding the README to describe re-encode mode, which remains intentionally excluded by the existing README contract.

## Decisions

### D1 — Establish an on-device FFmpeg configuration gate before writing a box rewriter

Create a focused diagnostic matrix against the Android FFmpeg build. Compare the current `+faststart+use_metadata_tags` command with a configuration that omits `use_metadata_tags`, and with any explicit location-metadata variant needed to make the muxer choose QuickTime location output. Run each variant against both device samples and both cut modes.

A candidate configuration is acceptable only if raw-container inspection confirms `moov/udta/©xyz` in the output and the existing logical metadata checks still pass for vendor tags, `creation_time`, stream properties, gallery date, and source immutability. The configuration must be selected based on the bundled Android build; desktop results are diagnostic only.

If a candidate passes, production continues to use FFmpeg for the cut and the change remains small. If no candidate passes, the implementation proceeds to D3 rather than relaxing the contract.

**Alternative considered:** changing only the metadata assertion to compare coordinates semantically. Rejected because it would hide the Google Photos compatibility failure and would not restore the required container representation.

### D2 — Model logical metadata and physical representation separately

Extend the probe model with a representation-level description for preservation-critical location metadata. The model should be able to report the logical value and whether the file contains:

- QuickTime `moov/udta/©xyz`;
- generic `mdta/location`;
- generic `mdta/location-eng`;
- multiple representations; or
- no recognized embedded location.

Use a small, dependency-free MP4 box walker for this structural information rather than parsing human-oriented FFprobe trace logs. The existing FFprobe probe remains the source for normalized format and stream tag maps. The walker only needs to understand MP4 box sizes, nesting, `udta`, `©xyz`, and the `meta`/`keys`/`ilst` structure required to identify `mdta` entries; it must not become a general media parser.

**Alternative considered:** parsing `FFprobeKit` trace output. Rejected as the primary implementation because log wording and escaped box names are less stable than the file format, and the result would be harder to reuse for the fallback writer.

### D3 — Preserve the raw source `©xyz` box if FFmpeg cannot emit it

The fallback metadata finalizer SHALL use the source file's raw `©xyz` box as the source of truth, not a reserialized coordinate string. It will parse the output's `moov` tree, ensure an appropriate `udta/©xyz` entry exists with the source payload, and retain the output's generic metadata entries so manufacturer-specific tags are not discarded.

The writer must account for MP4 box sizes and chunk offsets when changing the size or placement of `moov`. It must support the 32-bit and extended-size boxes encountered in the sample corpus and preserve media sample payloads. For a lossless output, the finalizer must not alter encoded packets; for re-encode output, it must only alter container metadata after encoding.

The generic `mdta/location` entries may remain in addition to `©xyz`; the compatibility requirement is that the QuickTime representation is present and unchanged, not that all equivalent representations be removed.

**Alternative considered:** rewriting only the MediaStore row's latitude/longitude. Rejected because the emulator experiment showed Google Photos recognizing the source's embedded `©xyz` even when MediaStore coordinates were null, while the cut output with generic metadata was not recognized.

### D4 — Surface representation provenance in both metadata cards

The source card and source/output diff will show the normalized location value and a compact representation status, such as `QuickTime ©xyz`, `generic mdta`, `both`, or `absent`. The diff must mark a change from `©xyz` to only `mdta` as a preservation-critical change even when the displayed coordinate strings are equivalent.

The UI will not expose raw binary payloads by default. It will display enough provenance to explain why a consumer may not recognize a logical tag, while retaining the existing expandable tag inventory for normalized values.

### D5 — Keep the README explanation user- and contributor-oriented

Update the existing "How It Works" section to explain that MP4 metadata has physical representations, that FFprobe can normalize them, and that the QuickTime `©xyz` form matters for gallery compatibility. The explanation will mention that both Pixel 10a and Xiaomi Poco samples contain this atom, without exposing unnecessary coordinate data in the README.

### D6 — Test structure, not just normalized FFprobe maps

Retain the existing logical metadata assertions and add representation assertions for every supported sample in both modes. The test matrix must verify source/output `©xyz` presence and payload equivalence, vendor tags, creation time, orientation, gallery date, and source immutability. Existing known re-encode rotation failures remain visible; tests must not be weakened or made to pass by excluding the new representation checks.

The app end-to-end test will continue to verify the pick-to-save path and raw metadata through the cache/output files. Manual Google Photos verification remains a release acceptance check because Google Photos is an external consumer whose UI is not a stable test API.

## Risks / Trade-offs

- **[Risk] FFmpeg flag behavior differs between desktop FFmpeg and the bundled Android build.** → Run the decision matrix on-device before changing production flags, and retain the raw-box fallback.
- **[Risk] Removing `use_metadata_tags` may restore `©xyz` but drop or rewrite vendor-specific tags.** → Require the full existing tag matrix to pass before selecting the configuration.
- **[Risk] MP4 box rewriting can invalidate chunk offsets or corrupt a file.** → Implement a narrow parser/writer with 32-bit and extended-size handling, keep media payloads untouched, and test round trips against both samples.
- **[Risk] A raw box writer may encounter MP4 layouts not represented by the current samples.** → Fail the cut with a surfaced explanation when the structure is unsupported rather than silently emitting an output with uncertain metadata.
- **[Risk] Google Photos behavior varies by app version and device.** → Use raw atom assertions as the deterministic contract and retain on-device Google Photos verification as a compatibility check.
- **[Risk] Location values are sensitive.** → The viewer already displays embedded location to the user who selected the file; the new representation fields reveal storage form, not additional location data, and no location leaves the device.

## Migration Plan

1. Add the representation probe and diagnostic tests without changing production cut behavior.
2. Run the Android FFmpeg flag matrix against both samples and both cut modes.
3. If a flag-only configuration passes, switch the engine to it and keep the raw-box writer out of the production path.
4. Otherwise implement and enable the raw `©xyz` preservation finalizer, with an explicit failure for unsupported MP4 structures.
5. Update the source/output metadata cards and README.
6. Run unit/build checks and the engine/app instrumented suites; separately perform Google Photos verification on the connected device.
7. Rollback is limited to reverting the engine metadata finalization selection; the original cut output remains a new file and is never modified in place.
