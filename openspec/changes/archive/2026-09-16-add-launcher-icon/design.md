## Context

See `proposal.md` — Why, for the motivation. This section covers only the state
and constraints that shape the approach.

**The resources exist and are correct; only the wiring is missing.** `git status`
shows a complete Android Studio Image Asset export — five density buckets × three
rasters (`ic_launcher`, `ic_launcher_round`, `ic_launcher_foreground`), two
adaptive-icon XMLs, `@color/ic_launcher_background`, and the 512×512
`ic_launcher-playstore.png`. Density dimensions are exactly the Image Asset
contract: 108/162/216/324/432 for the adaptive canvas, 48/72/96/144/192 for the
legacy rasters. The export is not the problem.

**Nothing references it.** `android:icon` is absent from `<application>`, and a
grep for `ic_launcher` across `app/src` finds only the two adaptive-icon XMLs
referring to each other. Confirmed against the last-built debug APK:
`aapt2 dump badging` → `icon=''`.

**Precedent is one commit away.** The sibling project
`~/Dev/personal/android-bluetooth-bouncer` solved this in commit `a44ef86` — 21
files, 16 insertions, of which the only logic is the same two manifest
attributes. That commit has **no** `<monochrome>` layer, which is why
`launcher-identity`'s existing shape and this one diverge.

**Adaptive-icon geometry drives the sizing decision.** The canvas is 108dp and
the mask reveals only the central 66dp — about 61% of the drawn area. The
exported foreground fills the canvas, so roughly 39% of the artwork is
mask-dependent. Which 39% is launcher-specific, which is why this cannot be
settled by looking at one render.

**A usable emulator exists.** `ANDROID_AVD_HOME` is
`~/.config/.android/avd` (not the default `~/.android/avd`), and it contains the
`Medium_Phone` AVD the README names as verified:
`system-images/android-37.0/google_apis_playstore_ps16k/arm64-v8a`. The
`google_apis_playstore` tag matters — it ships a Pixel-family launcher with
switchable icon shapes and themed-icon support, which is what makes the two
verification decisions below testable rather than argued.

## Goals / Non-Goals

**Goals:**

- The mark renders on launcher and system surfaces, verified under a real
  launcher rather than inferred from a generated preview.
- The mask-safety decision is *tested* across the shapes one launcher offers, not
  asserted from the single mask Image Asset happens to render.
- The deliberate absence of a monochrome layer survives the next person to
  regenerate the asset set.
- `launcher-identity` gains its first automated coverage. Its three existing
  requirements are entirely manual today.

**Non-goals:**

- A `values-night` variant of `@color/ic_launcher_background`. The value is
  `#E1E1E1`, a light neutral, while the app's night window background is
  `#FF141218`. **Confirmed by observation**: in dark Settings and on a dark home
  screen the `#E1E1E1` disc is the single bright element on screen — an icon
  disc, not the splash defect originally assumed here. It is deferred for
  a reason specific to this surface: launchers are not consistent about
  re-resolving *app* resources on a `uiMode` change, so the fix's effect can be
  sticky until the launcher restarts. Worth doing, but it needs its own
  observation on a real device to know whether it lands.
- An Android 12+ splash-screen theme. Deferred, and **its justification here
  turned out to be wrong.** This entry originally predicted the mandatory
  framework splash would resolve to `?android:colorBackground` and so flash
  light on a dark cold start. Measured in night mode it paints `#141218` —
  exactly this app's own night `windowBackground` — so the framework does
  honour the themed window background and there is no full-screen flash. The
  remaining case for a splash theme is brand presentation, not a defect.
  Bouncer's history — icon in `a44ef86`, splash as a separate archived change —
  is still the ordering this follows. See `notes/launcher-icon.md`.
- A dedicated monochrome artwork layer. See Decision 1.
- A separate, more-filled 512 composition for the Play Store listing. See Open
  Questions.

## Decisions

### Decision 1: Ship no monochrome layer, not a fake one

Image Asset emitted `<monochrome android:drawable="@mipmap/ic_launcher_foreground"/>`
in both adaptive-icon XMLs — pointing at the **colour** foreground. That is the
bug this change fixes, not a feature.

The monochrome slot has a contract: the artwork is single-colour on transparent,
because themed-icon launchers take that layer's *alpha* and flood it with the
system accent. The Tagalong mark is five-colour artwork with heavy dark outlines.
Flooded, it becomes a solid accent silhouette where the film reel is swallowed by
the hashtag — worse than an un-themed icon.

| Option | Themed-icon result | Verdict |
|---|---|---|
| Keep as-is | Accent-tinted silhouette; the mark is lost | Reject — actively wrong |
| Dedicated monochrome artwork | Proper themed icon; hashtag + reel as a clean cut-out | Best outcome, but needs a black-on-transparent source. `icon.png` is a flattened 1024px PNG, so this is an art task, not a config task |
| **Remove the declaration** | Launcher applies its own derived treatment to the standard icon | **Chosen** |

Removing the declaration is *honest by default*: an undeclared layer is an
unclaimed capability. The launcher's derived treatment is an approximation, but it
is the system's approximation, and it is not presented as designed artwork.

**Consequence accepted:** AGP's `MonochromeLauncherIcon` warning fires. No
`lint {}` block, no baseline file, and the release Dockerfile runs only
`assembleRelease` with no lint step — so it cannot fail a build. If it becomes
noise, suppress it per-file. Re-adding a fake layer to silence a lint warning
would reintroduce exactly the defect being removed.

### Decision 2: Record the decision in the manifest, not only here

`AndroidManifest.xml` in this repo is unusually well-argued — the
`ACCESS_MEDIA_LOCATION` permission and the `ACTION_SEND` filter each carry their
rationale in place, for Play review and for the next reader. The `icon` attribute
follows that house style with two sentences on why `monochrome` is absent.

This is load-bearing, not decorative. Image Asset re-adds `<monochrome>` from its
Monochrome page on every regeneration. A rationale that lives only in this
`design.md`, three directories away, will not be read at the moment the asset
wizard is open.

### Decision 3: 55% foreground fill, verified across available masks

The foreground was regenerated during implementation: 60% → 50% → **55% final**.
55% is the setting verified on-device. On the Pixel circle mask every extremity
(both upper arms, the left and right mid-height bars, both lower legs, the
filmstrip tail) keeps its rounded dark cap with visible margin before the mask
edge. It is preferred over 50% because at 50% the reel's six holes and the
filmstrip were marginal at drawer size — 55% buys legibility without reaching
the mask.

But that composite is **one** mask, produced by **the tool that generated the
asset** — the least adversarial available view of this exact decision. So the
sizing requirement is verified by sweeping the shapes the emulator's launcher
offers, including the square mask, which reveals the *largest* region of the
canvas and therefore bounds how much can be clipped elsewhere.

Chosen over simply accepting the preview because the clipping is
device-dependent: the mask that clips is likely one not in front of us.

### Decision 4: Verify in layers; automate what can be automated

Four layers, each kept for what it uniquely proves:

| Layer | Command / action | Uniquely proves |
|---|---|---|
| Static wiring | `assembleDebug`, then `aapt2 dump badging` \| grep icon | The fix exists in the package — `icon=''` → real `application-icon` + adaptive-icon entries |
| Source assertion | `grep -rn monochrome app/src/main/res/` → no matches | Decision 1 applied, at the level the spec states it |
| Instrumented | new `LauncherIdentityTest` | See Decision 5 |
| Visual | install on `Medium_Phone`, screenshot drawer / mask sweep / themed icons / recents / Settings → Apps | The bug actually fixed, and the only check that sees a real launcher |

**`aapt2` is deliberately not sufficient on its own.** It proves the manifest
declares an icon; the bug is that the user *sees a green robot*. Conversely the
screenshots prove nothing about the package contents and would not catch a
mipmap that failed to package. Both are needed, neither is redundant.

### Decision 5: Automate what is verifiable, refuse what only looks verifiable

The shipped assertion is `ApplicationInfo.icon != 0` **plus a resource-entry-name
check** against `ic_launcher` — not the sketch below, and not a bare id check.

```kotlin
// originally drafted as; both lines are weaker than they look
val info = pm.getApplicationInfo(packageName, 0)
assertThat(info.icon).isNotEqualTo(0)
assertThat(info.loadIcon(pm)).isInstanceOf(AdaptiveIconDrawable::class.java)
```

Two defects in that draft, found by running it rather than reading it:

- **An id-only check is too weak.** It accepts any non-zero resource, so a
  copy-paste pointing `android:icon` at the round variant — or any other
  drawable — passes while the app ships the wrong mark. Pinning the entry name
  to `ic_launcher` is what gives it teeth.
- **The `AdaptiveIconDrawable` check is vacuous, and was.** It was written, and
  it **passed against the unfixed tree**: with `android:icon` absent,
  `getApplicationIcon()` returns the platform default app icon, which is itself
  an `AdaptiveIconDrawable`. It survives only by being *ordered after* the
  identity pin, where the placeholder can no longer satisfy it.

`ApplicationInfo.icon == 0` remains the precise programmatic signature of the
missing attribute — the test fails on the unmodified tree and passes after, with
no screenshots involved. It uses this repo's plain
`org.junit.Assert.assertTrue`-with-message-first convention (`E2eCutTest`), not
the truth-library style above.

**Two things deliberately not automated**, both of which look like obvious
improvements to add later and are called out in the test file for that reason:

- **Monochrome absence via `AdaptiveIconDrawable.getMono()`** (API 33). It
  looked ideal — the machine-readable form of Decision 1, and a guard against
  Image Asset regressing it. But its behaviour when *no* monochrome layer is
  declared is unverified; AOSP may return a non-null wrapper drawing the
  foreground. An assertion written against that guess would pass for the wrong
  reason. Monochrome absence stays at source level (`grep`) plus the manifest
  comment.
- **`android:roundIcon`.** `PackageItemInfo.icon` is the only public icon field
  — `ApplicationInfo` exposes no `roundIcon`, confirmed against `android.jar` —
  and `getApplicationIcon()` folds the round variant in with nothing observable
  to distinguish it. The tempting substitute,
  `resources.getIdentifier("ic_launcher_round", ...)`, asserts only that the
  *file* exists — which was already true while this bug was live, since the whole
  asset export was committed. It would pass against the unfixed tree. So
  `roundIcon` is verified statically by `aapt2 dump xmltree`, noting that
  `dump badging` does not report it at all.

### Decision 6: Keep the diff small and the assets as exported

Two attributes and one comment in the manifest; one element removed from each of
two XMLs. Four lines of hand-written change.

`mipmap-anydpi-v26/` is retained rather than renamed to `mipmap-anydpi/` despite
`minSdk 31` making the `-v26` qualifier redundant, and the per-density legacy
rasters are retained despite the adaptive XML always winning on API ≥ 26. Both
are standard Image Asset output, roughly 150 KB total, and the legacy rasters are
the fallback for any consumer that cannot inflate an `AdaptiveIconDrawable`.
Hand-pruning either buys nothing and makes the next regeneration produce a
spurious diff.

`ic_launcher-playstore.png` under `app/src/main/` is outside `res/` and therefore
never packaged — it is the Play Console listing asset, and it stays tracked
because it is the only home for the full-bleed square composition. Placement
matches bouncer deliberately.

## Risks / Tradeoffs

- **The emulator is the lenient family** → the `google_apis_playstore` Pixel
  launcher uses loose masks; Samsung One UI and Xiaomi HyperOS are tighter or
  irregular, and `sample-videos/xiaomi-poco-x5.mp4` shows Xiaomi is a device this
  project cares about. Mitigated by the square-mask upper bound and the teardrop
  sweep, but not eliminated. Physical-device confirmation stays a pre-release
  item, alongside the rows already in `AGENTS.md`.
- **Removing the monochrome layer forgoes designed themed icons** → a
  themed-icon user sees a system-derived approximation. Accepted in Decision 1:
  an approximation is better than a claim the artwork doesn't support. A real
  monochrome layer remains a follow-up once a suitable source exists.
- **Image Asset silently reverts Decision 1** → the manifest comment from
  Decision 2 is the guard, and the `grep` check in `tasks.md` is the reproducible
  check. This is a regression class, not a one-time risk.
- **`MonochromeLauncherIcon` lint warning now appears** → accepted, cosmetic, no
  lint step in CI. Suppress per-file if it earns attention; never fix by
  re-adding the layer.
- **50% may be smaller than ideal at 48dp** → the film-reel interior and the
  filmstrip tail are already illegible at drawer size, and shrinking further
  trades detail for margin. Mitigated by checking drawer-size legibility in the
  visual pass, and by the spec's legibility scenario, which is stated as an
  observation to make rather than a number to hit.
- **Installing over a previous build changes the app icon in place** → some
  launchers cache icons and show the stale placeholder until relaunch. Not a
  defect; verify with an uninstall-and-install if a stale icon is seen, before
  investigating further.

## Migration Plan

Additive resource wiring. No data model, no persisted state, no permissions, no
dependency, no `applicationId` change — installs as an update over any previous
version.

Rollback is the inverse of two edits: delete the two manifest attributes and
restore the `<monochrome>` element. The regenerated 50% assets are independent of
the wiring and can be kept or reverted separately, which keeps a partial revert
possible — for example, ship the corrected inset while reverting the manifest if
a launcher-specific problem appears.

## Open Questions

- **Should the Play Store 512 be its own composition?** Image Asset regenerates
  `ic_launcher-playstore.png` from the same source setting, so it inherited the
  50% inset. The listing asset is **not masked** — Play renders it as a plain
  square — so it has no safe-zone constraint, and inset artwork there is lost
  whitespace next to competitors that fill the frame. This is a real divergence
  between two consumers of one setting, currently decided by a shared checkbox.
  Deferred rather than decided because it does not affect the APK, the specs, or
  any task here; it is settled when the store listing is prepared. If it is
  answered "separate composition", the resolution is an art export, not a code
  change, and it will require decoupling that file from Image Asset's output.
- **Is a layered or vector source for the mark available?** `icon.png` is a
  flattened 1024px PNG. This is the gate on Decision 1's rejected "best" option —
  a genuine monochrome layer — and on any future re-brand. Nothing here waits on
  it; it determines whether a themed-icon follow-up is ten minutes or an art
  task.
