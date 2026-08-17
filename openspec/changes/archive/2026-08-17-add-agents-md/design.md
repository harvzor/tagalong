## Context

See proposal.md — Why. The repo currently has `tagalong-overview.md` (product context) but no agent-facing guide. The fixture `xiaomi-poco-x5.mp4` sits loose at the repo root; it is not referenced by any code — both `:engine` and `:cutdebug` carry their own copies under `src/androidTest/assets/`, which is where `TestFixtures.kt` reads from.

## Goals / Non-Goals

**Goals:**
- `AGENTS.md` at repo root: concise, structured, agent-readable context covering the topics the spec requires
- `CLAUDE.md` symlink → `AGENTS.md`
- `fixtures/xiaomi-poco-x5.mp4`: the original fixture in a tidy location

**Non-Goals:**
- Modifying any build files or test code (the fixture move is organisational only)
- Duplicating product context from `tagalong-overview.md`
- Writing a design document for AGENTS.md itself — the content is self-describing

## Decisions

### D1 — AGENTS.md content structure

Sections in order:

1. **Read first** — one-line pointer to `tagalong-overview.md` (product context lives there)
2. **Module layout** — table of `:app` / `:engine` / `:cutdebug` with role and frozen status
3. **Tech stack** — ffmpeg-kit version, Compose/Material3, minSdk 31; Media3 Transformer is out
4. **Toolchain rules** — AGP 9 built-in Kotlin (no `org.jetbrains.kotlin.android`), no `kotlinOptions {}`, use `./gradlew.bat`, default branch `master`
5. **Build & test** — how to boot the AVD, run connected tests
6. **OpenSpec workflow** — brief description of the `openspec/` directory, proposals, apply, archive
7. **Current state** — step 1 done (lossless pick→trim→save), step 2 next (mode toggle + re-encode)
8. **Known gaps** — rotation-in-reencode bug, location redaction gap, with pointers to where the detail lives

Rationale: sections flow from "orient yourself" to "build the project" to "know what's broken." An agent starting cold reads top-to-bottom and has everything it needs by the end.

### D2 — CLAUDE.md as a symlink

A symlink (`New-Item -ItemType SymbolicLink`) is preferred over a second file so there is exactly one source of truth. On Windows, `mklink` or PowerShell's `New-Item -ItemType SymbolicLink` both work; Git tracks symlinks correctly on Windows when `core.symlinks=true` (the default for repos with symlink support).

Alternative: copy the content. Rejected — two files diverge.

### D3 — fixtures/ folder

Create `fixtures/` at repo root, move the mp4 there. No build or test changes needed; both modules copy the file into their `src/androidTest/assets/` directories directly.

## Risks / Trade-offs

- **Symlink on Windows**: Git on Windows requires `core.symlinks=true` and Developer Mode (or admin) to create real symlinks. If the symlink can't be created (e.g. CI without those permissions), fall back to a plain file with a comment pointing to AGENTS.md. Confirm symlink creation succeeds before committing.
- **AGENTS.md staleness**: The file must be updated alongside code changes. This is a social contract, not a technical one — the spec's "kept current" requirement is the enforcement.
