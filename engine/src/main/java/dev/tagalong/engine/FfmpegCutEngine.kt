package dev.tagalong.engine

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

/**
 * Arm B — com.antonkarpenko:ffmpeg-kit-full-gpl (D3). Lossless mode uses the command
 * already validated on desktop (design.md D3); re-encode mode swaps `-c copy` for real
 * encoders but keeps the same seek/metadata flags.
 *
 * The metadata tail (the flags after the codec selection) is identical for both modes and
 * is assembled once in [executeAndFinalize], so every flag that shapes output metadata has
 * exactly one home and one comment. See design.md `cut-command-honesty` for why each flag
 * earns its place.
 */
class FfmpegCutEngine : CutEngine {
    override val name = "ffmpeg-kit"

    override fun losslessCut(source: File, startMs: Long, durationMs: Long, output: File) {
        executeAndFinalize(
            source = source,
            output = output,
            // Everything before the metadata tail: seek to the start, then copy every
            // stream unchanged. `-c copy` rewrites container only, never the media packets.
            args = buildList {
                add("-y")
                add("-ss"); add(secondsArg(startMs))
                add("-i"); add(source.absolutePath)
                add("-to"); add(secondsArg(durationMs))
                add("-c"); add("copy")
            },
        )
    }

    override fun reencodeCut(source: File, startMs: Long, durationMs: Long, output: File) {
        // Re-encoding decodes frames, so ffmpeg's default auto-rotate filter would
        // physically rotate the pixels to "upright" and drop the rotation signal — which
        // is exactly what the spec forbids ("Orientation is preserved as a signal ... not
        // baked into frames"). -noautorotate keeps the source's raw (unrotated) pixels.
        //
        // KNOWN GAP (see notes/rotation-reencode-gap.md): re-stamping that rotation onto
        // the freshly-encoded output stream so the *signal* survives does not currently
        // work via any CLI-only path on this ffmpeg build (8.1.1, bundled in
        // ffmpeg-kit-full-gpl 2.1.0) — `-metadata:s:v:0 rotate=N` is deprecated and
        // silently no-ops on the mov muxer, `-display_rotation` is input-only (errors if
        // applied output-side), and h264_metadata's Display Orientation SEI insertion,
        // while it does land in the bitstream, is not honored by Android's
        // MediaMetadataRetriever. Re-encode output is therefore pixel-correct (unrotated,
        // not baked-in) but currently loses the rotation *signal* — flagged for task 5.
        executeAndFinalize(
            source = source,
            output = output,
            args = buildList {
                add("-y")
                add("-noautorotate")
                add("-ss"); add(secondsArg(startMs))
                add("-i"); add(source.absolutePath)
                add("-to"); add(secondsArg(durationMs))
                add("-c:v"); add("libx264"); add("-preset"); add("veryfast"); add("-crf"); add("20")
                add("-c:a"); add("aac"); add("-b:a"); add("128k")
            },
        )
    }

    /**
     * Runs the mode-specific [args], then appends the metadata tail and the output path,
     * executes, and hands the freshly-written [output] to [Mp4LocationFinalizer].
     *
     * The source is probed exactly once, up front. That one [LocationRepresentationInfo]
     * decides whether ffmpeg's location mistranslation is suppressed (below) and is then
     * reused by the finalizer, so the source is never streamed twice.
     */
    private fun executeAndFinalize(source: File, output: File, args: List<String>) {
        val sourceInfo = Mp4LocationMetadata.inspect(source)

        val fullArgs = buildList {
            addAll(args)

            // ffmpeg's default, stated explicitly: copy the whole global metadata dictionary
            // from input 0. `:g` is the default scope ("global"); deleting this changes
            // nothing — a no-flag arm and this arm produce byte-identical metadata. Kept so
            // the reader sees where the dictionary comes from rather than trusting a default.
            add("-map_metadata"); add("0:g")

            // Two movflags, each earning its place:
            //  - use_metadata_tags: LOAD-BEARING. Writes the metadata dictionary in the
            //    Android mdta dialect (keys/ilst, the same shape the camera writes). Without
            //    it the vendor keys (com.android.*, com.xiaomi.*) are dropped and every
            //    mvhd/tkhd/mdhd date field is zeroed (verified 2026-09-22, desktop ffmpeg
            //    9.0.1 + exiftool on both corpus fixtures, both modes).
            //  - faststart: layout-only, kept as-is. It relocates moov before mdat so a
            //    file can be played while still downloading. No consumer of a locally-saved
            //    gallery file needs it, and it costs a second full-file write pass at mux
            //    completion — relevant on the >2 GB 4K cuts. Removal is deferred to its own
            //    change; keeping it preserves current on-device byte-for-byte behavior.
            add("-movflags"); add("+faststart+use_metadata_tags")

            // Do NOT let ffmpeg invent a location. Its mov muxer has no ©xyz write path for
            // MP4, so it re-encodes the normalized `location` into a 3GPP loci box (16.16
            // fixed-point — re-quantized, +52.5182 → 52.51819) plus a fabricated Altitude: 0,
            // AND, under use_metadata_tags, a verbatim dictionary copy. The source's true
            // ©xyz is restored after the cut by Mp4LocationFinalizer. Deleting the normalized
            // location entries here (so mov_write_loci_tag sees no location and bails:
            // `if (!t) return 0`) is the deletion half of that pair — the two must not ship
            // unpaired. Only for QuickTime-shape sources: a source whose location is only a
            // dictionary entry (no ©xyz) has no finalizer to restore it, so its location is
            // left untouched and ffmpeg's verbatim copy carries it, as before.
            if (sourceInfo.hasQuickTime) {
                add("-metadata"); add("location=")
                add("-metadata"); add("location-eng=")
            }

            add(output.absolutePath)
        }

        execute(fullArgs)
        runCatching { Mp4LocationFinalizer.preserve(source, output, sourceInfo) }
            .onFailure {
                output.delete()
                throw it
            }
    }

    private fun secondsArg(millis: Long): String = String.format(Locale.US, "%.3f", millis / 1000.0)

    private fun execute(args: List<String>) {
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        check(ReturnCode.isSuccess(session.returnCode)) {
            "ffmpeg failed (rc=${session.returnCode}):\n${session.allLogsAsString}"
        }
    }
}
