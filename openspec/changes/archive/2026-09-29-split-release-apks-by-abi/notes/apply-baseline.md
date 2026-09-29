# Apply baselines — split-release-apks-by-abi

Environment (measured 2026-09-26):
- Build host: macOS, JDK 25, Android SDK at ~/Library/Android/sdk
- **No `docker` on PATH; `podman` 5.8.2 is the available OCI builder.** Dockerfile
  container-build verification (tasks 2.4/2.5) is exercised by running the
  collection-step shell logic directly against the real host build-output tree
  instead of a full `docker build`, since a full build re-fetches the entire SDK +
  214.7 MB ffmpeg payload each run and podman differs on BuildKit secret syntax.
- Emulator: emulator-5554, API 37, `ro.product.cpu.abilist=arm64-v8a`.

## 1.1 Before figure — latest published release
- Tag `v0.7.0`, asset `tagalong-0.7.0.apk` = **243 MB** (single combined APK).

## 1.3 Emulator architecture
- `ro.product.cpu.abilist` = **arm64-v8a** — matches a planned artifact; group 4 target.

## 1.2 Baseline release output layout (unsplit)
`./gradlew :app:assembleRelease` — BUILD SUCCESSFUL. One release output:
- `app/build/outputs/apk/release/app-release-unsigned.apk` = **243 MB** (unsigned; no keystore locally).
- Flat layout under `apk/release/` (no per-abi subdirs). `output-metadata.json` + `baselineProfiles/` also present.
- Contamination is live on disk: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` coexists.
  Today's `find app/build/outputs/apk -name "*.apk"` selects BOTH → both would land on the single
  `tagalong-${VERSION}.apk` destination. Confirms the latent defect is reachable, not theoretical.

## 1.4 Baseline pass state (still unsplit)
- `:engine:testDebugUnitTest` (host, 256 MB heap pin): **green**.
- `:app:connectedDebugAndroidTest`: **5/5 pass, 0 fail**.
- `:engine:connectedAndroidTest`: **7 tests, 1 fail, 0 error**. The one failure is
  `CutEngineContractTest.reencodeCut_preservesAllSourceTagsIdentically`
  (`rotation signal expected:<90> but was:<0>` on both google-pixel-10a + xiaomi-poco-x5).
  This is the **known, documented re-encode rotation gap** (AGENTS.md → Known open gaps).
  **Group 4 regression = any failure OTHER than this single named test.**
