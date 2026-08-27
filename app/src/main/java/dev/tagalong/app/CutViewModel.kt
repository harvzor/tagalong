package dev.tagalong.app

import android.app.Application
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Fires once when a picked video is ready for trimming. HomeScreen collects this to
     *  navigate to the trim screen after the async materialise-and-probe work completes. */
    private val _navigateToTrim = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToTrim: SharedFlow<Unit> = _navigateToTrim.asSharedFlow()

    /** Uri → cache `File` (D1), then probe its duration. Resets any prior source/cut state. */
    fun onVideoPicked(uri: Uri) {
        _uiState.value = CutUiState(cutState = CutState.Idle)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    data class SourceMeta(
                        val displayName: String,
                        val relativePath: String?,
                        val absolutePath: String?,
                    )
                    // Resolve the source path. ACTION_OPEN_DOCUMENT can return URIs from
                    // several document providers; each has its own document-ID format:
                    //
                    //  • ExternalStorageProvider  (com.android.externalstorage.documents)
                    //    docId = "primary:DCIM/Camera/foo.mp4"
                    //    → the path after ':' is the relative path from the storage root.
                    //
                    //  • MediaDocumentsProvider   (com.android.providers.media.documents)
                    //    docId = "video:1234"
                    //    → the number after ':' is the MediaStore _ID; query via msUri.
                    //
                    // We decode each case to an absolute path and let everything fall back to
                    // a DISPLAY_NAME-only query on the document URI.
                    val meta: SourceMeta = runCatching {
                        val authority = uri.authority ?: ""
                        val docId = DocumentsContract.getDocumentId(uri)
                        when {
                            authority == "com.android.externalstorage.documents" -> {
                                // docId: "primary:DCIM/Camera/foo.mp4"
                                val relativePart = docId.substringAfter(':') // "DCIM/Camera/foo.mp4"
                                val name = relativePart.substringAfterLast('/')
                                val dir  = relativePart.substringBeforeLast('/', missingDelimiterValue = "")
                                val absPath = Environment.getExternalStorageDirectory().absolutePath +
                                    "/" + relativePart
                                val relPath = if (dir.isNotEmpty()) "$dir/" else null
                                SourceMeta(name, relPath, absPath)
                            }
                            authority == "com.android.providers.media.documents" -> {
                                // docId: "video:1234"
                                val rowId = docId.substringAfter(':').toLong()
                                val msUri = ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId
                                )
                                resolver.query(
                                    msUri,
                                    arrayOf(
                                        MediaStore.MediaColumns.DISPLAY_NAME,
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        MediaStore.MediaColumns.DATA,
                                    ),
                                    null, null, null,
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val name = cursor.getString(0)?.takeIf { it.isNotBlank() }
                                        val rel  = cursor.getString(1)?.takeIf { it.isNotBlank() }
                                        val data = cursor.getString(2)?.takeIf { it.isNotBlank() }
                                        val absPath = data
                                            ?: if (rel != null && name != null)
                                                Environment.getExternalStorageDirectory().absolutePath +
                                                    "/" + rel.trimEnd('/') + "/" + name
                                               else null
                                        SourceMeta(name ?: "video.mp4", rel, absPath)
                                    } else null
                                }
                            }
                            else -> null
                        }
                    }.getOrNull()
                    // Fall back to querying the document URI itself for DISPLAY_NAME only.
                    ?: resolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst())
                            SourceMeta(cursor.getString(0) ?: "video.mp4", null, null)
                        else null
                    }
                    ?: SourceMeta("video.mp4", null, null)

                    val displayName = meta.displayName
                    val displayPath = if (meta.relativePath != null) {
                        meta.relativePath.trimEnd('/') + "/" + displayName
                    } else displayName
                    val absolutePath = meta.absolutePath
                    val file = materializeToCache(uri)
                    val durationMs = readDurationMs(file)
                    val source = PickedSource(uri, file, durationMs, displayName, displayPath, absolutePath)
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
                _navigateToTrim.tryEmit(Unit)
            }.onFailure { e ->
                _uiState.value = CutUiState(cutState = CutState.Error(e.message ?: "Could not read the picked video"))
            }
        }
    }

    /** Resets the cut pipeline back to Idle (keeping the source and trim range intact) so that
     *  the TrimScreen's navigation LaunchedEffect does not re-trigger when returning from the
     *  ResultScreen. Called by ResultScreen before it pops the back stack. */
    fun resetCutState() {
        _uiState.value = _uiState.value.copy(
            cutState = CutState.Idle,
            outputProbe = null,
            outputCacheFile = null,
        )
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
                    val saveResult = DateTakenStore.registerAndReadBack(
                        context = getApplication(),
                        file = output,
                        captureTimeMillis = captureTimeMillis,
                        displayName = outputDisplayName,
                    )
                    Triple(saveResult, outputProbe, output)
                }
            }
            result.onSuccess { (saveResult, outputProbe, outputFile) ->
                _uiState.value = _uiState.value.copy(
                    cutState = CutState.Saved(
                        galleryDateMillis = saveResult.dateTakenMillis,
                        outputAbsolutePath = saveResult.absolutePath,
                    ),
                    outputProbe = outputProbe,
                    // Keep the cache File reference for ResultScreen playback. DateTakenStore
                    // copies from it rather than moving it, so it remains readable here.
                    outputCacheFile = outputFile,
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
