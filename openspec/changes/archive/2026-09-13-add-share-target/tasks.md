# Tasks: add-share-target

## 1. Manifest & intake plumbing

- [x] 1.1 Add the `ACTION_SEND` intent filter to `MainActivity` in `app/src/main/AndroidManifest.xml` (`DEFAULT` category, `video/*`, `scheme="content"`) and set `launchMode="singleTask"`
- [x] 1.2 In `MainActivity`, add a single share parser: extract `EXTRA_STREAM` from an `ACTION_SEND` intent, accept only a `content`-scheme `Uri` of video MIME (reject missing/String/`file://` payloads), returning null on anything unusable
- [x] 1.3 In `MainActivity.onCreate`, consume a usable launch-intent share before `setContent`: seed `CutViewModel.onVideoPicked(uri)` and pass `startDestination = "trim"` to the `NavHost` (else `"home"`); unusable/absent share → ordinary Home launch, no error UI
- [x] 1.4 Implement `onNewIntent` for re-shares into a live session: parse + consume via the same parser, call `onVideoPicked`, and navigate to `trim` with `launchSingleTop = true` via the Activity-held `NavHostController`; an `ACTION_MAIN` `onNewIntent` (launcher relaunch) must not restart the flow
- [x] 1.5 Gate replay on recreation: with `savedInstanceState != null`, treat a redelivered share intent as a fresh intake (accepted degradation, design D3); ensure a live-session relaunch never re-parses the stale share

## 2. Unredacted materialisation

- [x] 2.1 In `CutViewModel.materializeToCache`, add a `MediaStore.AUTHORITY` branch: when `ACCESS_MEDIA_LOCATION` is granted, open via `MediaStore.setRequireOriginal(uri)` with a `runCatching` fallback to the plain Uri on `SecurityException`/`UnsupportedOperationException`; non-media URIs keep the plain `openInputStream` path
- [x] 2.2 Resolve source metadata (`DISPLAY_NAME`, `RELATIVE_PATH`) for share-supplied media URIs so the Trim path label and output file naming keep working; reuse the row-id query shape from the existing `com.android.providers.media.documents` branch and keep the existing fallback chain for other URI shapes
- [x] 2.3 Confirm the immediate-materialise property holds for shares (copy completes while the ephemeral SEND grant is alive; downstream reads only the cache file) — add a code comment if not self-evident

## 3. Tests

- [x] 3.1 Instrumented test in `:app` (androidTest): launch `MainActivity` with an `ACTION_SEND` intent carrying a MediaStore video URI (corpus video ingested to MediaStore) and assert the UI reaches the Trim screen with the source loaded and Home never displayed
- [x] 3.2 Instrumented test: `ACTION_SEND` with no/absent `EXTRA_STREAM` → Home screen displayed, no error state in `CutUiState`
- [x] 3.3 Instrumented test (or ViewModel-level unit test): media-provider share with `ACCESS_MEDIA_LOCATION` granted → materialised cache file contains the source's GPS location tag (assert via `MetadataReader.probe` on the cache file); the spec's "sender-side" case needs no test beyond the existing diff/preserve coverage
- [x] 3.4 Run `./gradlew :app:testDebugUnitTest` and, with the verified AVD up, `./gradlew :app:connectedAndroidTest` — existing pick-flow and metadata-preservation suites stay green (`singleTask` regression check included)

## 4. Device verification (the empirical bet)

- [ ] 4.1 On the real Pixel 10a (and the Xiaomi device if available), share a known-location sample (from `sample-videos/`, pushed to the device gallery) to Tagalong from (a) Google Photos and (b) the AOSP/system gallery; record which URI authority/tag shape arrives
- [ ] 4.2 For each sender, `ffprobe` the materialised cache bytes (or the cut output) against the repository original: location, `creation_time`, device make/model tags; record the matrix (sender × device × tag) in the change directory as `notes/share-fidelity.md`
- [ ] 4.3 Manual end-to-end on one share: trim → cut → output appears in the gallery under the source capture date; Google Photos shows location on the output when the sender delivered it; portrait rotation plays correctly
- [ ] 4.4 Manual regression: launcher cold start → Home; in-app pick flow unchanged; launcher relaunch mid-session resumes without replaying the share; back from share-launched Trim exits to the sender; back from picked Trim returns Home; re-share while running replaces the source
- [x] 4.5 Decide from 4.2: acceptable → proceed; materially degraded versus the contract → propose revert of this change (accepted outcome, design D5/Migration)

## 5. Documentation

- [x] 5.1 Add an AGENTS.md section "Why the app also accepts ACTION_SEND" (Play-review rationale): user-explicit single-file grant, no new permissions, same preservation engine, sender-side fidelity documented honestly; cross-reference the existing picker and `ACCESS_MEDIA_LOCATION` sections rather than duplicating them
- [x] 5.2 Record the 4.2 fidelity matrix findings in the AGENTS.md section (which senders deliver originals) and note the limitation that the diff card is the honesty surface
- [x] 5.3 Update the AGENTS.md "OpenSpec workflow"/implementation-state table if the project maintains the step list, and mention the new share entry point in the README feature summary if one exists
