package dev.tagalong.engine

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * Diagnostic-only matrix for selecting the production metadata strategy on the bundled
 * Android FFmpeg build. It deliberately records failures instead of asserting that any
 * candidate is good: the raw-box preservation contract is tested separately.
 */
@RunWith(AndroidJUnit4::class)
class FfmpegMetadataDiagnosticTest {
    companion object {
        private const val TAG = "FfmpegMetadataMatrix"
    }

    private val context get() = TestFixtures.appContext()

    @Test
    fun recordBundledFfmpegLocationMatrix() {
        val diagnosticDir = File(context.cacheDir, "tagalong-diagnostic").apply { mkdirs() }
        TestFixtures.samples(context).forEach { sample ->
            val source = TestFixtures.sourceFile(sample, context)
            val sourceProbe = MetadataReader.probe(source)
            val sourceHash = FileAssertions.sha256(source)
            val interval = cutInterval(sourceProbe)

            CutMode.entries.forEach { mode ->
                variants(sourceProbe.formatTags["location"]).forEach variantLoop@{ variant ->
                    val output = File(
                        diagnosticDir,
                        "${sample.stem}-${mode.name.lowercase(Locale.ROOT)}-${variant.name.lowercase(Locale.ROOT)}.mp4",
                    ).apply { delete() }
                    val args = buildArguments(
                        source, output, interval.first, interval.second, mode, variant,
                        sourceProbe.formatTags["location"],
                    )
                    val session = FFmpegKit.executeWithArguments(args.toTypedArray())
                    if (!ReturnCode.isSuccess(session.returnCode)) {
                        Log.e(
                            TAG,
                            "${sample.fileName}/$mode/${variant.name}: ffmpeg failed " +
                                "rc=${session.returnCode}\n${session.allLogsAsString}",
                        )
                        return@variantLoop
                    }

                    val outputProbe = runCatching { MetadataReader.probe(output) }.getOrElse { failure ->
                        Log.e(TAG, "${sample.fileName}/$mode/${variant.name}: probe failed", failure)
                        return@variantLoop
                    }
                    val galleryDate = runCatching {
                        val captureTime = Instant.parse(
                            requireNotNull(sourceProbe.formatTags["creation_time"]),
                        ).toEpochMilli()
                        val displayName = "diagnostic-${sample.stem}-${mode.name}-${variant.name}.mp4"
                        DateTakenStore.registerAndReadBack(context, output, captureTime, displayName).dateTakenMillis
                            .also { deleteByDisplayName(displayName) }
                    }.getOrNull()
                    Log.i(
                        TAG,
                        buildString {
                            append("${sample.fileName}/$mode/${variant.name}: ")
                            append("xyz=${outputProbe.locationRepresentation.hasQuickTime}, ")
                            append("xyzPayload=${outputProbe.locationRepresentation.quickTimePayload?.toHex()}, ")
                            append("mdta=${outputProbe.locationRepresentation.genericMdtaKeys}, ")
                            append("vendor=${outputProbe.formatTags.filterKeys { it.startsWith("com.") }}, ")
                            append("creation_time=${outputProbe.formatTags["creation_time"]}, ")
                            append("rotation=${outputProbe.videoRotationDegrees}, ")
                            append("galleryDate=$galleryDate, ")
                            append("sourceUnchanged=${sourceHash == FileAssertions.sha256(source)}")
                        },
                    )
                    output.delete()
                    assertEquals(
                        "${sample.fileName}/$mode/${variant.name}: diagnostic must not modify source",
                        sourceHash,
                        FileAssertions.sha256(source),
                    )
                }
            }
        }
    }

    private fun variants(location: String?): List<Variant> = listOf(
        Variant.CURRENT,
        Variant.WITHOUT_USE_METADATA_TAGS,
        Variant.EXPLICIT_LOCATION,
    )

    private fun buildArguments(
        source: File,
        output: File,
        startMs: Long,
        durationMs: Long,
        mode: CutMode,
        variant: Variant,
        location: String?,
    ): List<String> = buildList {
        add("-y")
        if (mode == CutMode.REENCODE) add("-noautorotate")
        add("-ss"); add(secondsArg(startMs))
        add("-i"); add(source.absolutePath)
        add("-to"); add(secondsArg(durationMs))
        if (mode == CutMode.LOSSLESS) {
            add("-c"); add("copy")
        } else {
            add("-c:v"); add("libx264"); add("-preset"); add("veryfast"); add("-crf"); add("20")
            add("-c:a"); add("aac"); add("-b:a"); add("128k")
        }
        add("-map_metadata"); add("0")
        if (variant == Variant.EXPLICIT_LOCATION) {
            val location = requireNotNull(location) { "Explicit variant requires a source location" }
            add("-metadata"); add("location=$location")
            add("-metadata"); add("location-eng=$location")
        }
        add("-movflags")
        add(if (variant == Variant.CURRENT) "+faststart+use_metadata_tags" else "+faststart")
        add(output.absolutePath)
    }

    private fun cutInterval(sourceProbe: MediaProbe): Pair<Long, Long> {
        val duration = requireNotNull(sourceProbe.durationMs)
        val start = minOf(500L, duration / 4)
        val length = minOf(3000L, duration - start)
        require(start > 0L && length > 0L)
        return start to length
    }

    private fun secondsArg(millis: Long): String = String.format(Locale.US, "%.3f", millis / 1000.0)

    private fun deleteByDisplayName(displayName: String) {
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                resolver.delete(
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        cursor.getLong(idColumn),
                    ),
                    null,
                    null,
                )
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private enum class Variant {
        CURRENT,
        WITHOUT_USE_METADATA_TAGS,
        EXPLICIT_LOCATION,
    }
}
