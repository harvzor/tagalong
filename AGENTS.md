# Tagalong — Agent Guide

**Read first:** [`tagalong-overview.md`](tagalong-overview.md) — the problem this app solves, the two cut modes, what metadata must be preserved, out-of-scope items, and design principles. Every decision in this repo is read against that document. Don't duplicate it here.

---

## Module layout

| Module | Role | Status |
|---|---|---|
| `:engine` | Production cut engine. `FfmpegCutEngine`, `CutEngine`/`CutMode`, `MetadataReader`, `DateTakenStore`. Contract tests in `engine/src/androidTest`. | Active — build the app against this |
| `:app` | Android UI (Compose/Material3). `CutScreen`, `CutViewModel`, `CutUiState`, `TrimRangeSlider`, `VideoPreview`. minSdk 31, targets SDK 36. | Active |
| `:cutdebug` | **Frozen.** Side-by-side bake-off harness from the cut-engine evaluation. `Media3CutEngineTest` tests are *expected to fail* — that's the documented finding. | Do not touch |

---

## Tech stack

- **Cut engine:** `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` (won the bake-off). **Media3 Transformer is out** — it drops all `com.android.*`/`com.xiaomi.*` tags and overwrites `creation_time` with the export timestamp.
- **UI:** Jetpack Compose + Material3, `androidx.compose:compose-bom:2026.04.01`
- **Video preview:** `androidx.media3:media3-exoplayer` + `media3-ui`
- **minSdk 31**, compileSdk/targetSdk 36
- **Language:** Kotlin 2.3.20 via AGP's built-in Kotlin support

---

## Toolchain rules

These are **hard constraints** — violating them causes build errors, not warnings.

- **Do not add `org.jetbrains.kotlin.android` to any `plugins {}` block.** AGP 9+ bundles Kotlin support; adding the plugin is a fatal build error.
- **Do not add `kotlinOptions {}` blocks.** Use `compileOptions.sourceCompatibility`/`targetCompatibility` only.
- **Entry point:** `./gradlew` (macOS/Linux) or `.\gradlew.bat` (Windows PowerShell). Gradle 9.5.0, wrapper committed, no separate install needed
- **Default branch:** `master` (not `main`)
- **`local.properties`** is gitignored. Create it with `sdk.dir` set to your own Android SDK root — typically `%LOCALAPPDATA%\Android\Sdk` on Windows, `~/Library/Android/sdk` on macOS. Java properties files expand nothing, so write the path out literally, e.g. `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`

---

## Build & test

An emulator or device must be running before the instrumented suites; README's *Instrumented tests* section names the verified AVD image. The commands below assume one is already up.

```powershell
# Windows PowerShell
.\gradlew.bat :engine:connectedAndroidTest
```

```bash
# macOS / Linux
./gradlew :engine:connectedAndroidTest
```

Confirm whatever you started is visible:

```bash
adb devices
adb -s emulator-5554 emu avd name
```

Test corpus: `sample-videos/` (repo root) is the canonical source for device-originated samples, including `xiaomi-poco-x5.mp4` and `google-pixel-10a.mp4`. Both active modules package this directory as their `androidTest` assets; there are no module-local copies. Instrumented tests discover every supported sample automatically, so adding a video here expands the engine and app metadata-preservation matrix without a test-source registration change. The repository files remain human-readable and `ffprobe`-accessible.

Host-side unit tests (`:engine:testDebugUnitTest`) run without an emulator. The `:engine` unit-test JVM is deliberately pinned to `maxHeapSize = "256m"` to simulate the stock Android per-app heap; do not raise it to make a whole-file read pass (see `Mp4LocationHeapBudgetTest`).

### Pre-release manual verification

Before shipping a release, in addition to the automated suites:

- Cut a multi-minute 4K clip (file larger than the app memory class) on the real Pixel 10a and confirm: the cut succeeds, Google Photos shows the location on the saved output, and it plays normally. The emulator corpus is 11–16 MB and cannot exercise the large-file paths.
- Complete the share-fidelity matrix on physical devices (share a known-location sample to Tagalong from Google Photos and the system gallery; confirm the received bytes and cut output carry location + capture date). Score sheet: `notes/share-fidelity.md` under the `add-share-target` change directory (follow it into `openspec/changes/archive/` when the change archives). Emulator row already green; a materially degraded physical-device row is grounds for reverting the share target, per that change's Migration Plan.

---

## Why the app uses ACTION_OPEN_DOCUMENT instead of the Photo Picker

> This section exists to support Play Store review responses. The reasoning below is also reproduced as a code comment on the `OpenDocument` launcher in `CutScreen.kt`.

This app's primary function is **lossless video trimming with complete metadata preservation** — every container tag in the source (GPS location, `creation_time`, device make/model, brand-specific tags) must survive the cut unchanged. The Android Photo Picker (`PickVisualMedia` / `ACTION_PICK`) makes this impossible:

| What the Photo Picker breaks | Why it cannot be worked around |
|---|---|
| **GPS location tags** — the Google Photo Picker module (`com.google.android.providers.media.module`) strips `location` and `location-eng` tags from the `openInputStream` byte stream, regardless of `ACCESS_MEDIA_LOCATION` being declared **or granted at runtime** (verified on-device 2026-08-23: granting the permission via `RequestPermission` before launching `PickVisualMedia` still produced output with no location tag) | No public API exists to request an unredacted byte stream through the Photo Picker path; `MediaStore.setRequireOriginal` throws `UnsupportedOperationException` on the Play Store module |
| **Real filename** — `DISPLAY_NAME` is replaced with the picker's internal numeric ID (e.g. `1000000072`) | The output file would inherit a meaningless name the user did not give it |
| **Gallery path** — `RELATIVE_PATH` is nulled out | The path label shown to the user while trimming would be incomplete |

`ACTION_OPEN_DOCUMENT` is the standard Android mechanism for granting an app **direct, persistent, unredacted access to a single file the user explicitly selects**. The app requests no broad media permissions and accesses only the file the user picks. This is the narrowest permission model that satisfies the metadata-preservation contract that is the app's reason for existing.

## Why the app also accepts ACTION_SEND

> This section exists to support Play Store review responses.

Tagalong additionally registers as a target for `ACTION_SEND` (`video/*`) so a user can share a video from their gallery straight into the editor instead of opening the app and re-picking the same file. This does **not** widen the app's permission model:

- **The user still explicitly selects one file.** The share sheet is the selection gesture; `ACTION_SEND` grants the receiving app read access to exactly that one URI, for that one session. The app requests no new permission, no broad media access, and continues to touch only the file the user handed it.
- **The preservation engine is unchanged.** A shared video goes through the identical materialise → probe → cut pipeline; every tag present in the received byte stream is carried to the output unchanged, and the result screen's metadata diff reports source → output tag survival honestly.
- **Fidelity of the received bytes is the sender's decision, not the app's.** A gallery app that hands over a redacted "share copy" (e.g. with location removed by the sender's own privacy setting) is outside the cut contract, which is about preserving metadata *through the cut*, not about the sending app's behavior before the hand-off. Where the shared URI references the system media provider directly, the app reads the unredacted original (`MediaStore.setRequireOriginal` + `ACCESS_MEDIA_LOCATION`, the documented mechanism). Sender-side absence is never hidden: the diff card shows the tag as absent on the source side, and the app displays no share-specific warnings, prompts, or permission requests of its own.
- **Location data still goes nowhere.** As with the picker path (see below), GPS values already embedded in the user's own file are read solely to be preserved into the user's own output file. No network, no backend, no analytics — unchanged by this entry point.

Measured fidelity so far (2026-09-13): Google Photos handing a gallery video to Tagalong delivered a **byte-identical original** — every tag, GPS included, arrived and survived the cut (verified on the emulator's Google Photos; the physical-device matrix is on the Pre-release manual verification checklist below and is recorded in change `add-share-target`, `notes/share-fidelity.md`). The result screen's metadata diff card is the honesty surface for any sender that behaves differently: a share copy already stripped by the sending app shows as absence on the source side of the diff, never hidden and never excused.

## Why the app declares ACCESS_MEDIA_LOCATION

> This section exists to support Play Store review responses.

Even with `ACTION_OPEN_DOCUMENT`, Android's media framework strips GPS location tags from the `ContentResolver.openInputStream` byte stream for apps that have not been granted `ACCESS_MEDIA_LOCATION` (API 29+). The stripping occurs at the `MediaDocumentsProvider` layer — it is not specific to the Photo Picker path — so any app that materialises a picked video via `openInputStream` without this permission will receive a byte stream with location already removed, regardless of which file picker was used.

`ACCESS_MEDIA_LOCATION` is declared and requested so that the framework delivers an unredacted byte stream. The permission is used exclusively to **read** GPS coordinates that are already embedded in a specific video file the user explicitly selects; it is never used to track the user's current location, infer movement, or share location data with any third party. The app has no analytics, no network calls, and no backend — there is nowhere for location data to go beyond the output video file saved to the user's own gallery.

---

## OpenSpec workflow

`openspec/` is the planning home. The project uses the **spec-driven** schema.

| Directory | Purpose |
|---|---|
| `openspec/specs/<capability>/spec.md` | Live capability specs — the current behavioral contract |
| `openspec/changes/<name>/` | Active or recently applied change (proposal → specs delta → design → tasks) |
| `openspec/changes/archive/` | Completed changes, kept for history |
| `openspec/config.yaml` | Schema and project config |

Key commands: `openspec new change "<name>"`, `openspec status --change "<name>"`, `openspec instructions <artifact> --change "<name>" --json`, `openspec validate --change "<name>"`, `openspec archive --change "<name>"`.

---

## Current implementation state

| Step | Description | Status |
|---|---|---|
| 0 | Move proven engine into shippable `:engine` module | ✅ Done (2026-08-16) |
| 1 | Thinnest lossless slice: pick → trim → save to gallery | ✅ Done (2026-08-17, commit e9506d6) |
| 1b | Share-to-Tagalong direct-to-trim entry point (`add-share-target`) | ✅ Code + tests done, verdict KEEP (2026-09-13); physical-device fidelity rows on the pre-release checklist |
| 2 | Mode toggle + re-encode | 🔲 Next |
| 3 | Keyframe-snap caveat, error states, polish | 🔲 Later |

Step 1 was verified end-to-end on the `Pixel_7_API_34` emulator: gallery date matched source `creation_time`, source bytes unchanged, portrait rotation signal survived, HEVC/mp4 processed cleanly. Verification has since moved to the `Medium_Phone` AVD (Android 17 / API 37) named in README's *Instrumented tests* section.

The picker was subsequently switched from `PickVisualMedia` to `ACTION_OPEN_DOCUMENT` (change `switch-picker-to-open-document`, 2026-08-19): GPS location tags, real `DISPLAY_NAME`, and `RELATIVE_PATH` are now all preserved end-to-end without permission workarounds. Verified by `E2eCutTest` and manual `ffprobe` on cut output.

---

## Known open gaps

### 🐛 Rotation signal lost in re-encode mode (blocks step 2)

`FfmpegCutEngine.reencodeCut` does not re-stamp the container rotation signal onto the freshly-encoded output stream. Pixels are correctly left unrotated (`-noautorotate`), but the display matrix is absent — portrait clips play sideways.

Every CLI option was tried and ruled out (see `openspec/changes/archive/2026-08-16-cut-engine-bakeoff/notes/rotation-reencode-gap.md`). Likely fix: mux the re-encoded video through `androidx.media3:media3-muxer`'s `Mp4Muxer` (accepts `Format.rotationDegrees` per track) instead of ffmpeg's mov muxer. **Must be fixed before re-encode mode ships.**
