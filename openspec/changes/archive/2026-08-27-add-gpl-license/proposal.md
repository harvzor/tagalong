## Why

The repo has no license file, which means it defaults to all-rights-reserved under copyright law. Because the `:engine` module depends on `com.antonkarpenko:ffmpeg-kit-full-gpl`, any distributed build must be GPL-compatible; GPL v3 is the correct and legally required choice.

## What Changes

- Add `LICENSE` file in the repo root containing the full GPL v3 text

## Capabilities

### New Capabilities

- `licensing`: Tracks the project's license requirements — what license applies, the copyright holder, and what every source file must carry.

### Modified Capabilities

_(none)_

## Impact

- Repo root: new `LICENSE` file
- All `.kt` files in `app/src/` and `engine/src/`: one-line copyright header added
- No build changes, no runtime changes, no API changes
