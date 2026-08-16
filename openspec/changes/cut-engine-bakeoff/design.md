## Context

See proposal.md — Why. Tagalong has one guarantee (metadata survives a trim, including the gallery date). Desktop ffmpeg has already confirmed the lossless command preserves all file-level tags on a real Xiaomi clip, but two things remain unproven and are only testable on-device: whether a shippable Android engine reproduces that result, and whether the gallery date (`MediaStore.DATE_TAKEN`) can be preserved at all. There are two plausible engines and no way to choose from docs. This change is a spike that answers both questions with one test run against both engines.

Constraints that shape the approach:
- ffmpeg-kit's native `.so` binaries and `MediaStore` both exist only on Android, so the meaningful test is an **instrumented test** (`androidTest`) on a device/emulator — not a JVM unit test.
- Environment already present: adb 1.0.41, Java 25, desktop ffmpeg/ffprobe 8.1.1. Missing: exiftool (optional; ffprobe covers the file-level tags).
- The bake-off must be apples-to-apples: one harness, one fixture, one assertion set, two engines.

## Goals / Non-Goals

**Goals:**
- Prove or disprove the `metadata-preserving-cut` contract on a real device for each engine.
- Produce a clear, evidence-backed engine decision (Media3 Transformer vs antonkarpenko ffmpeg-kit).
- Build the reusable metadata-verification method (source ⊆ output tag diff + `DATE_TAKEN` check) that later app changes will reuse.

**Non-Goals:**
- No app: no UI, file picker, trim slider, mode toggle, or navigation.
- No production build toolchain (that is a later change; here we use only what the test module needs).
- No ffmpeg-kit-next: it publishes no prebuilt package and needs a Nix-built AAR — deferred as the long-term migration target of the ffmpeg arm, not spiked now.
- Not solving keyframe-snap UX — only noting the drift so the test tolerates it.

## Decisions

**D1 — Two-arm bake-off, shared harness.** One instrumented test drives an `CutEngine` interface with two implementations (Media3, ffmpeg). Same fixture, same assertions. Rationale: only a shared harness makes the comparison trustworthy and makes deleting the loser a one-file change. Alternative (separate throwaway projects per engine) rejected: duplicated assertions drift and can't be compared.

**D2 — Media3 Transformer is Arm A (the hoped-for winner).** `androidx.media3:media3-transformer` + `media3-muxer`. If it preserves all tags and the gallery date, tagalong ships native, Apache-2.0, small, hardware-accelerated, same family as the future Compose UI. Its lossless path is transmuxing (no effects → remux without transcode). Risk is metadata fidelity, which is exactly what the test measures.

**D3 — antonkarpenko ffmpeg-kit is Arm B (the proven fallback).** `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` — the same engine LibreCuts ships today. Command already validated on desktop: `-ss <start> -i <src> -to <dur> -c copy -map_metadata 0 -movflags +faststart+use_metadata_tags <out>`. Chosen over the retired arthenica original (frozen) and over ffmpeg-kit-next (no prebuilt package). The command string is identical across all three, so migrating the winning ffmpeg arm to ffmpeg-kit-next later is a dependency swap, not a rewrite.

**D4 — Preservation = "no source tag lost."** Per the user: added tags (e.g. ffmpeg's `encoder`) are acceptable; only dropped source tags fail. The assertion is `source_tags ⊆ output_tags` by key and value, not tag-set equality.

**D5 — Gallery date is set explicitly, not assumed.** A newly written file defaults to `DATE_TAKEN = now`. Each engine's output is registered via `MediaStore` with `DATE_TAKEN` set to the source's capture date (parsed from the source `creation_time`), then read back after a media scan. This is the step LibreCuts omits and the one most likely to expose a difference between engines.

**D6 — Verification uses ffprobe-equivalent tag reads on-device.** The ffmpeg arm can probe via its own binary; for the Media3 arm and for a neutral cross-check, read container tags via `MediaExtractor`/`MediaMetadataRetriever` and compare against the source. Desktop ffprobe remains the manual ground-truth for spot checks.

**D7 — Both modes are tested per engine.** Lossless and re-encode each run the full assertion set, satisfying the "holds identically in both modes" requirement. An engine passing lossless but failing re-encode (or vice versa) fails the contract.

## Risks / Trade-offs

- **Media3 Transformer drops manufacturer-specific tags (`com.android.*`, `com.xiaomi.*`)** → This is the most likely way Media3 fails the contract. The primary fixture `xiaomi-poco-x5.mp4` now carries the full camera tag set (`com.android.version/manufacturer/model`, `com.xiaomi.product.marketname`), so the test can detect this failure directly. No secondary fixture is needed.
- **Neither engine preserves `DATE_TAKEN` reliably** → This would be a product-level finding, not a bug. Surfacing it now (before app code) is the entire point of sequencing this spike first. Mitigation: the spike's negative result feeds back into product scope.
- **ffmpeg-kit-full-gpl is a large binary + GPL** → Accepted for the spike; it only ships if it wins. Documented so the licensing implication is a conscious choice, not a surprise.
- **antonkarpenko fork is single-maintainer / retired upstream** → Mitigated by D3: the command is portable to ffmpeg-kit-next later.
- **Keyframe snapping makes lossless cut points drift (~seconds)** → Confirmed on desktop (a 3s request produced 2.80s). Not a defect. Mitigation: the test asserts on metadata, not exact duration, and tolerates duration/size differences per the overview.
- **Bleeding-edge toolchain (bouncer's AGP 9.2 / Kotlin 2.3 / Gradle 9.5) may clash with the ffmpeg-kit AAR** → Out of this change's critical path (no production toolchain here), but the spike module's Gradle setup is the first place a clash would show. Mitigation: keep the spike module's toolchain minimal and record any version it forces.

## Open Questions

- Physical device vs emulator for the canonical run? Emulator is reproducible; a physical Xiaomi would most faithfully reproduce the manufacturer tags. (Deferrable: run on both if available.)
