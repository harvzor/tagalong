# Design: add-share-target

## Context

See `proposal.md` — Why, for motivation. The constraints that shape the approach:

- The app's contract is *preserve every tag in the bytes the engine reads*. The in-app flow guarantees those bytes are unredacted via `ACTION_OPEN_DOCUMENT` + `ACCESS_MEDIA_LOCATION` (`CutViewModel.materializeToCache` → plain `openInputStream`). A share hands the byte stream over through a **third-party sender**, which the app cannot police.
- `MainActivity` is a single-Activity Compose `NavHost` (`home` → `trim` → `result`, plus `about`). The home → trim handoff today rides `CutViewModel.navigateToTrim`, a `MutableSharedFlow(extraBufferCapacity = 1)` with **no replay**, collected only by `HomeScreen`'s `LaunchedEffect`. A share that emits before Home's collector subscribes would strand the event; a share that renders Home for one beat before sliding away is a visible lie ("Pick video" — already done).
- Share URIs are heterogeneous: a gallery may hand a plain MediaStore row URI (`content://media/...`, granted read), a sender-app `FileProvider` cache copy, or (rarely, from sloppy senders) a `file://` URI or a String instead of a `Uri`. `materializeToCache` handles none of the MediaStore specifics today — and for plain media URIs, `openInputStream` returns location-redacted bytes even with `ACCESS_MEDIA_LOCATION` granted unless `MediaStore.setRequireOriginal()` is applied first. (The AGENTS.md note that `setRequireOriginal` throws applies to the *Play picker module*, not the ordinary media provider — different path, and here the permission-gated call is the documented mechanism.)
- `MainActivity` is `launchMode` `standard`: a re-share while running would stack a second Activity instance, and the launch intent is redelivered on recreation (process death), replaying stale shares.

## Goals / Non-Goals

**Goals:**
- One intake path (`EXTRA_STREAM` Uri → `onVideoPicked`) with no new cut/pipeline code.
- Deterministic intent lifecycle: consume once, survive recreation, handle re-share.
- Unredacted reads wherever the *platform* (not the sender) is the redactor.
- Honest failure: unusable share → silent Home fallback; sender-side redaction → visible only through the existing metadata diff.

**Non-Goals:**
- `ACTION_SEND_MULTIPLE` (the filter deliberately omits it; N videos is an unscoped UX).
- Fidelity guarantees about sender apps — verified empirically, documented, not enforced.
- Any change to `:engine`, the picker flow, or the permission request flow.
- App shortcuts / "Edit in Tagalong" quick entries beyond the share sheet.

## Decisions

### D1 — `ACTION_SEND` only, `video/*` without a scheme restriction; single task

Manifest filter on `MainActivity`:

```
action SEND · category DEFAULT · data mimeType video/*   (NO android:scheme)
launchMode="singleTask"
```

**Amended after on-device finding (2026-09-13, emulator):** the original design carried
`scheme="content"` to bounce `file://` senders at the manifest layer. That made the app
*invisible to every share sheet*: the canonical `ACTION_SEND` carries its file in
`EXTRA_STREAM` with the intent's `data` field null, and choosers resolve with a type-only
query — a filter declaring a scheme cannot match a data-null intent, so `cmd package
query-activities -a SEND -t video/mp4` never listed Tagalong (verified: adding `-d
content://…` made it resolve). The scheme belongs in the runtime parser, where the
bounce already lands: `parseSharedVideo` rejects non-`content` URIs and String payloads
through the silent Home fallback, so `file://` senders are still excluded — just
*after* the user chose us, which is the only place the intent's real payload is visible.
A scheme-free filter is also what all the resolver-table rivals (Gmail, Drive, Messages)
declare.

- `video/*` keeps the app out of every text/image share sheet; `intent.type` is trusted only as a gate, never as proof of playability — the probe still decides.
- `singleTask` over the alternatives: `standard` stacks a second `MainActivity` per share and leaves the stale-intent problem intact; `singleTop` only dedupes when the instance is already on top (during a trim session it is, but not after navigating or backgrounding). `singleTask` gives one instance + `onNewIntent` for every arrival (launcher, re-share), which is exactly the "consume exactly once" contract in the spec. TaskAffinity stays default so the share joins the app's own task.

### D2 — Direct-to-trim via intent-dependent `startDestination`

`MainActivity` parses the launch intent **once** in `onCreate`, before `setContent`: usable share → consume it, seed `viewModel.onVideoPicked(uri)`, and pass `startDestination = "trim"` to the `NavHost`; otherwise `"home"` as today.

Rejected alternatives:
- *Home-with-preload* (start at Home, let `navigateToTrim` push): the SharedFlow has no replay, so correctness depends on the collector subscribing before the IO coroutine emits — a race the share path makes reachable (cold start, small file). And Home painting-then-sliding-away is a 300 ms lie.
- *Replaying-state handoff* (make the event a replayable state on `uiState`): fixes the race but retrofits the whole home→trim mechanism for a problem that conditional `startDestination` dissolves.

Consequence, accepted and spec'd: on a share-launched session there is no Home entry in the back stack, so system back from Trim exits to the sender — which is the correct "where did I come from" answer, since the user came *from the gallery*. An explicit Up affordance is not added; Trim keeps its current back behavior and the destination semantics differ per entry, which the home-screen delta now states.

`onNewIntent` (re-share into a live session): parse + consume the same parser, call `onVideoPicked`, and `navController.navigate("trim")` with `launchSingleTop = true` from the Activity-held nav reference — Trim re-renders on the new `PickedSource`; no SharedFlow needed on this path since the collector problem doesn't apply (the caller navigates directly).

### D3 — Consumption discipline: consume in the ViewModel, gate recreation on `savedInstanceState`

- A `shareConsumed` flag lives in `CutViewModel` (survives rotation; `onSaveInstanceState`/`savedInstanceState != null` covers process death: on recreation the intent is re-delivered but the back stack and ViewModel-less state are rebuilt fresh, so treating a redelivered share as a *new* intake after process death is the accepted degradation — re-materialising beats showing an empty Trim with a dead Uri).
- Launcher relaunch is solved by `singleTask` + `onNewIntent`: `getIntent()` is replaced by the `ACTION_MAIN` intent, so the stale share cannot be re-parsed.
- The URI grant's lifetime (SEND grants are ephemeral, tied to the recipient's task) is respected by keeping the existing design property: materialisation to cache happens *immediately* in `onVideoPicked`, so the copy runs while the grant is certainly alive; everything downstream reads the cache file, never the Uri.

### D4 — `materializeToCache` gains a media-provider branch using `setRequireOriginal`

```
uri.authority == MediaStore.AUTHORITY ?
   ├─ permission granted → openInputStream(MediaStore.setRequireOriginal(uri))
   │                       (runCatching: SecurityException/Unsupported → fall back to plain uri)
   └─ otherwise          → openInputStream(uri)          ← framework redacts, flow proceeds
   else (FileProvider etc.) → openInputStream(uri)        ← sender's bytes, take them as handed
```

`setRequireOriginal` is the documented unredaction mechanism for media URIs and the only one; the fallback keeps the "never blocked by permission state" invariant. Source-metadata resolution (`DISPLAY_NAME`/`RELATIVE_PATH` for the Trim path label and output naming) already degrades through the existing query-fallback chain when `DocumentsContract.getDocumentId` throws on a non-document URI — a media URI additionally gets a direct `MediaStore` query by row id, which is the same query the existing `com.android.providers.media.documents` branch performs.

### D5 — Fidelity is an empirical finding, not a design premise

The sender decides the bytes; the engine preserves them; the diff card tells the truth. No sender detection, no per-sender behavior, no share-specific warnings. The change carries a device-verification task (ffprobe the materialised cache bytes vs. the `sample-videos/` originals, across Google Photos and a plain gallery, on Pixel 10a and the Xiaomi corpus video) whose result is recorded in `AGENTS.md` next to the existing picker rationale — this doubles as the Play-review section explaining why accepting `ACTION_SEND` is consistent with the no-new-permissions, metadata-preserving story.

Explicit posture (user decision, 2026-09): *try it and see; full revert is an accepted outcome* if verification shows the share path yields an experience materially worse than the in-app flow. The migration plan below carries that.

## Risks / Trade-offs

- [Sender strips GPS/device tags (e.g. a Photos "share copy")] → Engine contract unaffected (preserves what it receives); diff card shows source-side absence honestly; device verification quantifies which senders do this; AGENTS.md documents it. Worst case is a UX expectation mismatch, mitigated by copy if needed in a follow-up.
- [`singleTask` changes task behavior for the launcher flow too] → Launcher relaunches now route to the existing instance via `onNewIntent` instead of potentially creating a second instance — which is the desired behavior anyway; manual test that the in-app pick flow is untouched.
- [`setRequireOriginal` throws on some OEM provider implementations] → `runCatching` fallback to the plain Uri: degrade to redacted bytes, never a hard failure.
- [Huge 4K share via a sender cache copy → double disk pressure (sender copy + our cache copy + cut output)] → Unchanged from the picker path in kind, bounded by cache-dir behavior; already an accepted property of `materializeToCache`; noted for the manual large-file check on the real Pixel.
- [Process death mid-session replays the share as a fresh intake] → Accepted degradation (D3): user sees Trim with the video loaded rather than a broken state; the cut is idempotent from Trim.
- [`intent.type` lies (video/* declared, bytes unprobeable)] → The existing "Failures are surfaced, never silent" error path in the ViewModel handles it; unusable-at-parse → Home fallback, unreadable-at-probe → existing error UI.

## Migration Plan

Forward-only: manifest + Activity/ViewModel additions, no data migration, no engine or persisted-state changes. Ship behind normal rollout; device verification (tasks §Verify) gates *keeping* the feature: if the matrix shows share output is materially worse than the documented contract in a way users would experience as metadata loss, revert the single change wholesale (no spec or data residue).

## Open Questions

- Should a share-launched Trim get an explicit "Home" affordance (top bar) for users who want to start another clip? Deferred — the cut→result→trim loop already supports another clip, and the back-to-sender semantics are spec'd; revisit only if real use suggests otherwise.
- Does the share sheet place Tagalong in *recommended* targets once the filter exists (system-side ranking we don't control)? Observation only, no task.
