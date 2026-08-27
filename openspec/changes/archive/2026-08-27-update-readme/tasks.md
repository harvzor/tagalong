## 1. Draft README content

- [x] 1.1 Read the current `README.md` and the existing spec at `specs/repository-readme/spec.md` to confirm scope
- [x] 1.2 Read `AndroidManifest.xml` to confirm the exact permissions declared by the app
- [x] 1.3 Write the new `README.md`:
  - Heading: `Tagalong: Video Cutter`
  - Tagline: `Trim and convert video. Your metadata comes along.`
  - Intro paragraph: names the problem, states what Tagalong does, folds in "what it isn't" (no timeline, no filters, no cloud, one operation)
  - "What gets preserved" table: creation date, GPS location, camera info, orientation, gallery date — with a note that gallery date is stored separately from container metadata
  - "How It Works" section: ffmpeg-kit vs Media3 rationale, ACTION_OPEN_DOCUMENT vs Photo Picker rationale, keyframe-snapping caveat
  - "Install" section: link to GitHub Releases
  - Permissions table: each declared permission with plain-language explanation; ACCESS_MEDIA_LOCATION entry explains the OS-layer GPS stripping
  - Building section: retained from current README, lightly edited for consistency
  - Releases / Signing section: retained from current README, lightly edited for consistency
  - No mention of re-encode mode

## 2. Validate against spec

- [x] 2.1 Verify every requirement in `specs/repository-readme/spec.md` is satisfied:
  - Product statement readable from the first paragraph alone
  - Metadata table covers all five items with the gallery-date note
  - Install section links to GitHub Releases
  - "How It Works" covers ffmpeg-kit, Photo Picker, and keyframe-snapping
  - Permissions table explains ACCESS_MEDIA_LOCATION in terms of OS-layer GPS stripping
  - Building and Releases content retained
  - Re-encode mode not mentioned anywhere
