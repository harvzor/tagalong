## 1. Fixture folder

- [x] 1.1 Create `fixtures/` directory at the repo root and move `xiaomi-poco-x5.mp4` into it
- [x] 1.2 Verify no source file references the repo-root mp4 path (no code changes expected)

## 2. AGENTS.md

- [x] 2.1 Write `AGENTS.md` at the repo root following the section order in design.md D1: Read first → Module layout → Tech stack → Toolchain rules → Build & test → OpenSpec workflow → Current state → Known gaps
- [x] 2.2 Verify AGENTS.md covers every required topic from the spec (module roles, tech decisions, toolchain rules, OpenSpec workflow, current state, known gaps) and does NOT duplicate content from `tagalong-overview.md`

## 3. CLAUDE.md symlink

- [x] 3.1 Create `CLAUDE.md` as a symbolic link targeting `AGENTS.md` (PowerShell: `New-Item -ItemType SymbolicLink -Path CLAUDE.md -Target AGENTS.md`)
- [x] 3.2 Confirm the symlink resolves correctly: reading `CLAUDE.md` returns the same content as `AGENTS.md`
- [x] 3.3 If symlinks are not supported in this environment, create `CLAUDE.md` as a plain file with a note pointing to `AGENTS.md` and record the fallback

## 4. Verification & commit

- [x] 4.1 Run `openspec validate --change add-agents-md` and confirm no errors
- [x] 4.2 Commit: `git add fixtures/ AGENTS.md CLAUDE.md && git commit -m "Add AGENTS.md, CLAUDE.md symlink, and fixtures/ folder"`
