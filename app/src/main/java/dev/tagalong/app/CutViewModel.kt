package dev.tagalong.app

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
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
                    val file = materializeToCache(uri)
                    val durationMs = readDurationMs(file)
                    PickedSource(uri, file, durationMs)
                }
            }
            result.onSuccess { source ->
                _uiState.value = CutUiState(
                    source = source,
                    startMs = 0L,
                    endMs = source.durationMs,
                    cutState = CutState.Idle,
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

                    DateTakenStore.registerAndReadBack(
                        context = getApplication(),
                        file = output,
                        captureTimeMillis = captureTimeMillis,
                        displayName = "tagalong-${System.currentTimeMillis()}.mp4",
                    )
                }
            }
            result.onSuccess { galleryDate ->
                _uiState.value = _uiState.value.copy(cutState = CutState.Saved(galleryDate))
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
}
