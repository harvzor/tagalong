## Why

The current README reads as an internal technical reference rather than a project introduction. It leads with a Photo Picker justification table and has no clear statement of what the app is or isn't. A developer or user arriving at the repo gets no sense of Tagalong's purpose, scope, or value before hitting implementation detail.

## What Changes

- Replace the current `README.md` with a restructured document that leads with the problem and the one-sentence product statement
- Add an explicit "what gets preserved" table covering all five metadata obligations (creation date, GPS, camera info, orientation, gallery date)
- Add an "Install" section pointing to GitHub Releases
- Move the Photo Picker and ffmpeg-kit rationale into a "How It Works" section framed for understanding, not justification
- Restructure the permissions table with plain-language explanations
- Retain the existing Building and Releases sections, lightly edited for consistency
- Fold the "what it isn't" framing into the intro paragraph rather than giving it its own section
- Do not mention re-encode mode (not yet implemented)

## Capabilities

### New Capabilities

- `repository-readme`: The README as a user-facing document — what it must communicate, the audience it addresses, and the content obligations it carries.

### Modified Capabilities

*(none)*

## Impact

- `README.md` at the repo root — the only file changed
- No code, build system, or spec-level behavior changes
