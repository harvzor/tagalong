## Why

AI agents working in this repo have no structured entry point for module layout, tech decisions, toolchain rules, and known gaps — only the product-focused `tagalong-overview.md`. Without it, every session re-derives the same context or misses critical constraints (AGP 9 rules, frozen `:cutdebug`, open bugs). `AGENTS.md` codifies this as a first-class repo requirement.

## What Changes

- Add `AGENTS.md` at the repo root: the authoritative agent context file, covering module layout, tech stack, toolchain rules, OpenSpec workflow, current implementation state, and known open gaps.
- Add `CLAUDE.md` as a symlink to `AGENTS.md` so Claude Code picks it up automatically on session start.
- Move `xiaomi-poco-x5.mp4` from the repo root into `fixtures/` and update the one reference in `engine/src/androidTest` (`TestFixtures.kt`).
- `tagalong-overview.md` is kept; `AGENTS.md` references it for product context rather than duplicating it.

## Capabilities

### New Capabilities

- `agent-guide`: The repo SHALL maintain a machine-readable `AGENTS.md` at the root that gives AI agents sufficient context to work correctly without re-deriving established decisions.

### Modified Capabilities

*(none — no existing spec requirements change)*

## Impact

- `AGENTS.md`, `CLAUDE.md` (new files at repo root)
- `fixtures/xiaomi-poco-x5.mp4` (moved from root)
- `engine/src/androidTest/java/dev/tagalong/engine/TestFixtures.kt` (path update)
