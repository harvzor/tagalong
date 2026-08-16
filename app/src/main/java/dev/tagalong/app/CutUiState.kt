package dev.tagalong.app

import android.net.Uri
import java.io.File

/** The picked source, materialized to a cache `File` the engine can read (design D1). */
data class PickedSource(
    val uri: Uri,
    val file: File,
    val durationMs: Long,
)

/** State of the cut pipeline itself (task 4.3) — separate from whether a source is picked. */
sealed interface CutState {
    data object Idle : CutState
    data object Working : CutState
    data class Saved(val galleryDateMillis: Long?) : CutState
    data class Error(val message: String) : CutState
}

data class CutUiState(
    val source: PickedSource? = null,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val cutState: CutState = CutState.Idle,
)
