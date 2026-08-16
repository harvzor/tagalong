package dev.tagalong.cutdebug

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

/**
 * Arm B — com.antonkarpenko:ffmpeg-kit-full-gpl (D3). Lossless mode uses the command
 * already validated on desktop (design.md D3); re-encode mode swaps `-c copy` for real
 * encoders but keeps the same seek/metadata flags.
 */
class FfmpegCutEngine : CutEngine {
    override val name = "ffmpeg-kit"

    override fun losslessCut(source: File, startMs: Long, durationMs: Long, output: File) {
        execute(
            listOf(
                "-y",
                "-ss", secondsArg(startMs),
                "-i", source.absolutePath,
                "-to", secondsArg(durationMs),
                "-c", "copy",
                "-map_metadata", "0",
                "-movflags", "+faststart+use_metadata_tags",
                output.absolutePath,
            )
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
        val args = buildList {
            add("-y")
            add("-noautorotate")
            add("-ss"); add(secondsArg(startMs))
            add("-i"); add(source.absolutePath)
            add("-to"); add(secondsArg(durationMs))
            add("-c:v"); add("libx264"); add("-preset"); add("veryfast"); add("-crf"); add("20")
            add("-c:a"); add("aac"); add("-b:a"); add("128k")
            add("-map_metadata"); add("0")
            add("-movflags"); add("+faststart+use_metadata_tags")
            add(output.absolutePath)
        }
        execute(args)
    }

    private fun secondsArg(millis: Long): String = String.format(Locale.US, "%.3f", millis / 1000.0)

    private fun execute(args: List<String>) {
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        check(ReturnCode.isSuccess(session.returnCode)) {
            "ffmpeg failed (rc=${session.returnCode}):\n${session.allLogsAsString}"
        }
    }
}
