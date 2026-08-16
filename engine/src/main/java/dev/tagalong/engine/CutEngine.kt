package dev.tagalong.engine

import java.io.File

/**
 * One engine implementation under bake-off (D1). Both cut modes must write a
 * *new* output file and must never modify [source] — spec "The original file
 * is never modified".
 */
interface CutEngine {
    val name: String

    /** Stream-copy cut: no re-encode; cut points snap to the nearest keyframe. */
    fun losslessCut(source: File, startMs: Long, durationMs: Long, output: File)

    /** Re-encode cut: frame-accurate cut points; pixels/codec may change. */
    fun reencodeCut(source: File, startMs: Long, durationMs: Long, output: File)
}

enum class CutMode { LOSSLESS, REENCODE }

fun CutEngine.cut(mode: CutMode, source: File, startMs: Long, durationMs: Long, output: File) =
    when (mode) {
        CutMode.LOSSLESS -> losslessCut(source, startMs, durationMs, output)
        CutMode.REENCODE -> reencodeCut(source, startMs, durationMs, output)
    }
