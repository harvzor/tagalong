package dev.tagalong.app

import android.net.Uri
import dev.tagalong.engine.MediaProbe
import java.io.File

/** The picked source, materialized to a cache `File` the engine can read (design D1). */
data class PickedSource(
    val uri: Uri,
    val file: File,
    val durationMs: Long,
    val originalDisplayName: String,
    /** Gallery-relative path + filename (e.g. `DCIM/Camera/video.mp4`), or filename only
     *  when `RELATIVE_PATH` is unavailable (design D2). */
    val displayPath: String,
    /** Full absolute storage path (e.g. `/storage/emulated/0/DCIM/Camera/video.mp4`), or
     *  null when `RELATIVE_PATH` was unavailable and construction was not possible (design D2). */
    val absolutePath: String? = null,
)

/** State of the cut pipeline itself (task 4.3) — separate from whether a source is picked. */
sealed interface CutState {
    data object Idle : CutState
    data object Working : CutState
    data class Saved(
        val galleryDateMillis: Long?,
        /** Absolute storage path of the saved output (e.g. `/storage/emulated/0/Movies/Tagalong/…`),
         *  or null when the DATA column was not available on this device. */
        val outputAbsolutePath: String? = null,
    ) : CutState
    data class Error(val message: String) : CutState
}

data class CutUiState(
    val source: PickedSource? = null,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val cutState: CutState = CutState.Idle,
    val sourceProbe: MediaProbe? = null,
    val outputProbe: MediaProbe? = null,
    /** The cut output as a cache [File], available for playback on the result screen.
     *  The file lives in the app's cache dir and remains accessible without storage
     *  permissions (unlike the MediaStore-registered copy). */
    val outputCacheFile: java.io.File? = null,
)
