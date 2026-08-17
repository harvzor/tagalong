## Purpose

Defines the requirement that this repo maintains a machine-readable agent context file (`AGENTS.md`) that gives AI agents the working knowledge they need to contribute correctly without re-deriving established decisions each session.

## Requirements

### Requirement: AGENTS.md exists at the repo root and covers required topics

The repo SHALL have an `AGENTS.md` file at the root. It SHALL cover all of the following topics:

- Module layout and the role of each Gradle module (`:app`, `:engine`, `:cutdebug`)
- Key technology decisions (cut engine, UI stack, minSdk)
- Toolchain rules and known build gotchas
- OpenSpec workflow (what the `openspec/` directory is, how proposals and archives work)
- Current implementation state (what is done, what step is next)
- Known open bugs or gaps that affect upcoming work

The file SHALL be kept current as the implementation advances. A topic that no longer applies SHALL be removed; a new decision or gap that affects agent work SHALL be added.

#### Scenario: Agent reads AGENTS.md and finds module roles

- **WHEN** an AI agent reads `AGENTS.md` at the repo root
- **THEN** it can determine the role of each Gradle module without reading any build file

#### Scenario: Agent reads AGENTS.md and finds open bugs

- **WHEN** an AI agent reads `AGENTS.md`
- **THEN** it can identify known open bugs or gaps that block or constrain upcoming work

### Requirement: AGENTS.md references tagalong-overview.md for product context

`AGENTS.md` SHALL NOT duplicate the product context in `tagalong-overview.md`. It SHALL reference that file for the problem statement, mode definitions, preservation requirements, out-of-scope items, and design principles.

#### Scenario: Product context lives in exactly one place

- **WHEN** an agent looks for the rule "never silently re-encode"
- **THEN** it finds it in `tagalong-overview.md`, not duplicated in `AGENTS.md`

### Requirement: CLAUDE.md at repo root is a symlink to AGENTS.md

The repo SHALL contain `CLAUDE.md` at the root as a symbolic link targeting `AGENTS.md`. This ensures Claude Code loads the agent context file automatically on session start.

#### Scenario: CLAUDE.md resolves to AGENTS.md content

- **WHEN** `CLAUDE.md` is read
- **THEN** its content is identical to `AGENTS.md`
