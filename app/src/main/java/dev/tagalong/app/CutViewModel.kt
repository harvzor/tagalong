package dev.tagalong.app

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tagalong.engine.DateTakenStore
import dev.tagalong.engine.FfmpegCutEngine
import dev.tagalong.engine.MetadataReader
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the pick → trim → cut → save pipeline (design D1/D6). All I/O — the cache copy,
 * probing, the cut itself, and the MediaStore save — runs on [Dispatchers.IO], off the main
 * thread; failures are caught and routed to [CutState.Error] rather than left to crash or
 * silently vanish (spec "Failures are surfaced, never silent").
 */
class CutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CutUiState())
    val uiState: StateFlow<CutUiState> = _uiState

    /** Uri → cache `File` (D1), then probe its duration. Resets any prior source/cut state. */
    fun onVideoPicked(uri: Uri) {
        _uiState.value = CutUiState(cutState = CutState.Idle)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    // Query DISPLAY_NAME and RELATIVE_PATH from the picked URI.
                    // ACTION_OPEN_DOCUMENT returns the real filename in DISPLAY_NAME and the
                    // gallery-relative path in RELATIVE_PATH. displayPath falls back to
                    // filename-only if RELATIVE_PATH is unavailable (design D2).
                    val (rawDisplayName, relativePath) = resolver.query(
                        uri,
                        arrayOf(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.RELATIVE_PATH,
                        ),
                        null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst())
                            cursor.getString(0)?.takeIf { it.isNotBlank() } to
                                cursor.getString(1)?.takeIf { it.isNotBlank() }
                        else null to null
                    } ?: (null to null)
                    val displayName = rawDisplayName ?: "video.mp4"
                    val displayPath = if (relativePath != null) "$relativePath$displayName" else displayName
                    val file = materializeToCache(uri)
                    val durationMs = readDurationMs(file)
                    val source = PickedSource(uri, file, durationMs, displayName, displayPath)
                    // Probe is best-effort for display — a failure here must not block the pick.
                    val probe = runCatching { MetadataReader.probe(file) }.getOrNull()
                    source to probe
                }
            }
            result.onSuccess { (source, probe) ->
                _uiState.value = CutUiState(
                    source = source,
                    startMs = 0L,
                    endMs = source.durationMs,
                    cutState = CutState.Idle,
                    sourceProbe = probe,
                )
            }.onFailure { e ->
                _uiState.value = CutUiState(cutState = CutState.Error(e.message ?: "Could not read the picked video"))
            }
        }
    }

    /** Spec: "the start point cannot be set later than the end point." */
    fun onRangeChanged(startMs: Long, endMs: Long) {
        val source = _uiState.value.source ?: return
        val clampedStart = startMs.coerceIn(0L, source.durationMs)
        val clampedEnd = endMs.coerceIn(clampedStart, source.durationMs)
        _uiState.value = _uiState.value.copy(startMs = clampedStart, endMs = clampedEnd)
    }

    fun runCut() {
        val state = _uiState.value
        val source = state.source ?: return
        _uiState.value = state.copy(cutState = CutState.Working)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val engine = FfmpegCutEngine()
                    val probe = MetadataReader.probe(source.file)
                    val captureTimeMillis = Instant.parse(
                        requireNotNull(probe.formatTags["creation_time"]) { "Source has no capture date" }
                    ).toEpochMilli()

                    val output = File(getApplication<Application>().cacheDir, "cut-${System.currentTimeMillis()}.mp4")
                    engine.losslessCut(source.file, state.startMs, state.endMs - state.startMs, output)

                    // Probe the output while it is still a cache File (before DateTakenStore moves
                    // it into MediaStore, after which only a content:// URI is available and
                    // FFprobeKit cannot consume it). Best-effort: a failure here must not block save.
                    val outputProbe = runCatching { MetadataReader.probe(output) }.getOrNull()

                    val outputDisplayName = "${baseNameOf(source.originalDisplayName)}" +
                        "_from_${formatTimestamp(state.startMs)}" +
                        "_to_${formatTimestamp(state.endMs)}.mp4"
                    val galleryDate = DateTakenStore.registerAndReadBack(
                        context = getApplication(),
                        file = output,
                        captureTimeMillis = captureTimeMillis,
                        displayName = outputDisplayName,
                    )
                    galleryDate to outputProbe
                }
            }
            result.onSuccess { (galleryDate, outputProbe) ->
                _uiState.value = _uiState.value.copy(
                    cutState = CutState.Saved(galleryDate),
                    outputProbe = outputProbe,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(cutState = CutState.Error(e.message ?: "Cut failed"))
            }
        }
    }

    private fun materializeToCache(uri: Uri): File {
        val resolver = getApplication<Application>().contentResolver
        val extension = resolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "mp4"
        val file = File(getApplication<Application>().cacheDir, "input.$extension")
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open the picked video")
        return file
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: error("Could not read the video's duration")
        } finally {
            retriever.release()
        }
    }

    /** Produces `HH-MM-SS-mmm` from a millisecond offset, file-safe on all platforms. */
    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%02d-%02d-%02d-%03d".format(hours, minutes, seconds, millis)
    }

    /** Strips the file extension from a display name (e.g. `"foo.mp4"` → `"foo"`). */
    private fun baseNameOf(displayName: String): String = displayName.substringBeforeLast('.')
}
