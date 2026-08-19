## Context

See proposal.md — Why. The module is frozen and never compiled into the released APK; `:app` depends only on `:engine`.

The root `build.gradle.kts` declares `id("com.android.library") version "9.2.1" apply false`. This declaration is **shared** by `:engine`, so it must stay after `:cutdebug` is removed.

## Goals / Non-Goals

**Goals:**
- Remove all `:cutdebug` source, build config, and assets from the repo
- Leave `settings.gradle.kts`, root `build.gradle.kts`, `:app`, and `:engine` in a clean, buildable state

**Non-Goals:**
- Modifying any `:app` or `:engine` source
- Updating the bake-off archive (the findings are already complete and correct)

## Decisions

**Delete the directory entirely rather than just excluding from settings**

Alternatives considered:
- *Comment out `include(":cutdebug")` only* — leaves dead code and assets in the repo, still shows up in IDEs
- *Keep the directory, exclude from Gradle* — same problem; the fixture video in `src/androidTest/assets/` stays on disk

Full deletion is the clean answer. The archive captures everything worth keeping.

**No migration or rollback needed**

The module is not a dependency of anything that ships. Git history preserves the code if it's ever needed again.

## Risks / Trade-offs

- **Risk**: Someone assumes the bake-off code is gone and repeats the investigation.  
  → **Mitigation**: The archive in `openspec/changes/archive/2026-08-16-cut-engine-bakeoff/` is comprehensive and remains in the repo. `CLAUDE.md` references it.
