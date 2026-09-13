# Proposal: add-share-target

## Why

Editing a video currently requires opening Tagalong first, then re-finding the video inside the app's picker. Users naturally start at the video itself — they open the gallery, select a clip, and tap Share. Tagalong is absent from that share sheet because the app declares no `ACTION_SEND` filter, so the shortest path from "I want to trim this" to the trim screen is longer than it needs to be.

## What Changes

- Tagalong becomes a share target for `video/*` content URIs: selecting a video in any gallery/file app and sharing to Tagalong opens the app directly at the trim screen with that video loaded (direct-to-trim — the Home screen is not shown first).
- `MainActivity` gains share-intent handling: consume the incoming `EXTRA_STREAM` video URI exactly once, route it through the existing `onVideoPicked` pipeline (materialise → probe → trim), and handle re-shares while the app is already running.
- Materialising a share-supplied MediaStore URI requests the unredacted byte stream via `MediaStore.setRequireOriginal` (the existing `openInputStream` call is not sufficient for plain `content://media/...` URIs, even with `ACCESS_MEDIA_LOCATION` granted).
- A launch whose share intent carries no usable video URI falls back to the normal Home launch with no error.
- Back from a share-launched trim screen returns to the sending app (the gallery the user came from), not to Home; back from an in-app-picked trim screen still returns to Home.
- Cut semantics are unchanged: whatever tags are present in the received byte stream are preserved by the same engine and shown honestly in the result screen's metadata diff. Byte-stream fidelity now partly depends on the sending app (e.g. Google Photos may hand over a cache copy rather than the original file); this is accepted deliberately and verified empirically on real devices, and documented for Play review in `AGENTS.md`.

No new permissions are requested, and the in-app picker flow is unchanged.

## Capabilities

### New Capabilities

- `share-intake`: Receiving a video via the system share sheet — share-target declaration, one-shot intent consumption, direct-to-trim routing, unredacted materialisation of share-supplied MediaStore URIs, and honest handling of sender-dependent byte-stream fidelity.

### Modified Capabilities

- `home-screen`: "Home screen is the launch destination" is scoped to launcher launches — a share launch with a usable video URI starts at the trim screen instead. "Back navigation from Trim returns to Home" is scoped to launches that began at Home; a share-launched trim screen returns to the sending app.

## Impact

- **App manifest** (`app/src/main/AndroidManifest.xml`): `ACTION_SEND` intent filter on `MainActivity`; likely `launchMode="singleTask"` so re-shares route into the running instance instead of stacking activities.
- **`MainActivity`**: share-intent parsing and consumption; `startDestination` becomes launch-intent-dependent.
- **`CutViewModel`**: a share entry point feeding the existing `onVideoPicked` pipeline; `materializeToCache` gains a `setRequireOriginal` branch for MediaStore-authority URIs.
- **Specs**: new `share-intake` capability; `home-screen` delta (two requirement scopes).
- **Docs**: `AGENTS.md` gains a Play-review rationale section for accepting `ACTION_SEND` alongside the existing picker rationale; fidelity findings recorded after device verification.
- **Verification**: on-device matrix (Pixel 10a + Xiaomi source videos; Google Photos and gallery senders) ffprobing the materialised bytes against the repository originals. A negative fidelity result is an accepted outcome — the change ships only if the cut path itself is sound, and full revert is an accepted response if fidelity proves unacceptable.
- **Not affected**: `:engine` (no engine changes), `ACCESS_MEDIA_LOCATION` declaration/request flow, cut-result screen, in-app pick flow.
