package dev.tagalong.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.MediaInformation
import org.json.JSONObject
import java.io.File

data class MediaProbe(
    val formatTags: Map<String, String>,
    val videoStreamTags: Map<String, String>,
    val audioStreamTags: Map<String, String>,
    val videoRotationDegrees: Int?,
    val videoMime: String?,
    val audioMime: String?,
    val videoWidth: Int?,
    val videoHeight: Int?,
)

/**
 * Reads a cut output's metadata for the bake-off assertion set (task 2.2).
 *
 * D6 calls for reading container tags "via MediaExtractor/MediaMetadataRetriever" as a
 * neutral, engine-independent cross-check. In practice those public Android APIs only
 * expose a fixed set of well-known keys (date, location, rotation, mime) — there is no
 * public API to enumerate arbitrary format-level tags such as `com.android.manufacturer`
 * or `com.xiaomi.product.marketname`, which are exactly the tags this spike most needs to
 * verify (see design Risk #1). FFprobeKit — already a required dependency for Arm B — reads
 * the full tag dictionary by inspecting file bytes only; it doesn't care which CutEngine
 * produced the file, so it remains a fair, engine-independent judge for *both* arms'
 * output. Rotation and codec identity, which public API *does* cover, are cross-checked
 * via MediaMetadataRetriever/MediaExtractor as D6 originally specified.
 */
object MetadataReader {

    fun probe(file: File): MediaProbe {
        val info = ffprobeInformation(file)
        val videoStream = info.streams.firstOrNull { it.type == "video" }
        val audioStream = info.streams.firstOrNull { it.type == "audio" }

        return MediaProbe(
            formatTags = info.tags.toStringMap(),
            videoStreamTags = videoStream?.tags.toStringMap(),
            audioStreamTags = audioStream?.tags.toStringMap(),
            videoRotationDegrees = readRotationViaMediaMetadataRetriever(file),
            videoMime = readTrackMime(file, video = true),
            audioMime = readTrackMime(file, video = false),
            videoWidth = videoStream?.width?.toInt(),
            videoHeight = videoStream?.height?.toInt(),
        )
    }

    private fun ffprobeInformation(file: File): MediaInformation {
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        return session.mediaInformation
            ?: error("ffprobe failed to read ${file.name} (rc=${session.returnCode}): ${session.allLogsAsString}")
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = LinkedHashMap<String, String>()
        keys().forEach { key -> map[key] = optString(key) }
        return map
    }

    /** Public-API rotation signal — same source for both source and output, so any sign/scale
     * convention difference from ffprobe's raw display-matrix rotation cancels out in the comparison. */
    private fun readRotationViaMediaMetadataRetriever(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        } finally {
            retriever.release()
        }
    }

    private fun readTrackMime(file: File, video: Boolean): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (video && mime.startsWith("video/")) return mime
                if (!video && mime.startsWith("audio/")) return mime
            }
            null
        } finally {
            extractor.release()
        }
    }
}
