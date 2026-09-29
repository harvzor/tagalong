# Group 7 — release pipeline confirmed end-to-end on `v0.8.0-rc1`

Tag: `v0.8.0-rc1` → run 36630896028, **success in 4m33s** on `ubuntu-latest` with native Docker.

## Why this run mattered

It was the **first time the `Dockerfile` ever executed as a container build**. See
*Local container build attempt* below — the local podman attempts never reached a completed build.
The run specifically exercised the new `COPY scripts/ scripts/` layer and the delegation to
`scripts/collect-apks.sh`, which together replace the inline one-destination `find | cp` loop.

## Published assets

| Asset | Size | `apksigner verify` |
|---|---|---|
| `tagalong-0.8.0-rc1-arm64-v8a.apk` | 70 MB | VERIFIED |
| `tagalong-0.8.0-rc1-x86_64.apk` | 76 MB | VERIFIED |
| `tagalong-0.8.0-rc1-universal.apk` | 118 MB | VERIFIED |

Sizes match the local host build exactly. 7.3: exactly 3 of 3 assets present — one per `shippedAbis`
entry plus `universal`, none missing.

Signing: all three verify against the repository release key
(`CN=Bluetooth Bouncer, O=Harvey Williams, C=GB`, SHA-256 `2205c685…2b2a7d`), v2 scheme, 1 signer.
**All three share the one digest** — required, or switching artifact on a device would present as a
signature conflict rather than an architecture switch.

## Independently re-verified on the published files, not the local build

Downloaded the assets and re-ran the group 5 inspections against them:

| Artifact | `lib/` contents |
|---|---|
| arm64-v8a | `arm64-v8a` (11 `.so`) |
| x86_64 | `x86_64` (11 `.so`) |
| universal | `arm64-v8a` (11) + `x86_64` (11) — **no `armeabi-v7a`, no `x86`** |

This is the decisive confirmation of task 3.6 in the environment that actually ships. Before the
`ndk.abiFilters` fix this same artifact was 243 MB with 52 `.so` across four ABIs, 32-bit intact.

Also: `versionCode=800` / `versionName=0.8.0-rc1` identical across all three (task 3.4's
no-per-ABI-offset policy, confirmed in a real release). Pre-flight check before tagging: 800 > 700
(v0.7.0), so the RC is not a downgrade for existing installs.

Installed the CI-signed `arm64-v8a` artifact on the arm64-v8a emulator: installs, launches, renders
the home screen, `primaryCpuAbi=arm64-v8a`, **0 `UnsatisfiedLinkError`, 0 crashes**. This covers the
real release key, which the earlier local installs (throwaway key) could not.

## Release notes

Body was **empty** — the workflow passes neither `body` nor `generate_release_notes` to
`softprops/action-gh-release`, so nothing is auto-authored. Notes were therefore written by hand
(7.4), carrying the three things a reader needs: **243 MB → 70 MB**, uninstall-before-switch, and
the 32-bit "unsupported, not damaged" sentence, plus the three-artifact pick table.

Set `--prerelease`. Without it an `-rc` becomes GitHub's "Latest", and the README's Releases link
would point new users at a release candidate instead of `v0.7.0`.

## Local container build attempt — what was learned

Not a task requirement, but worth recording because the deferral was initially asserted rather than
tested. There is **no `docker` binary on this host**; only podman 5.8.2, which had never built this
image. podman cannot build this `Dockerfile` as written, for two reasons that both predate this
change and neither of which touches its logic:

1. **`RUN --mount=type=secret,…,env=VAR`** — the `env=` source is BuildKit-only. buildah rejects it
   (`secret should have syntax id=id[,target=path,required=bool,mode=uint,uid=uint,gid=uint`) while
   *resolving mountpoints*, i.e. before any command in that step runs. It is hardcoded inside the
   instruction, so no CLI flag works around it.
2. **`SHELL ["/bin/bash","-c"]` is ignored under OCI format** (podman warns explicitly). The
   assemble step needs bash for `${BUILD_TYPE^}`. Fixable with `podman build --format docker`.

Working around only (1) — via a temp copy diffed to prove the secret mounts were the sole change —
the build reached Gradle properly and failed at `AAPT2 Daemon startup failed`, an arm64-container /
rootless-podman limitation. CI is x86_64 native Docker where aapt2 works normally.

Reached **STEP 21/21** before each failure, which did usefully confirm: the SDK install, licence
acceptance, dependency warm-up and every `COPY` layer all work under podman — including the new
`COPY scripts/ scripts/` layer, and `.dockerignore` was verified not to exclude `scripts/`.

**Standing note for anyone else trying to build this image locally on macOS with podman:** it will
not work without forking the Dockerfile. Use CI, or real Docker.
