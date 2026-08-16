package dev.tagalong.cutdebug

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Arm A — androidx.media3:media3-transformer (D2). Lossless mode requests trim
 * optimization (transmux where the source allows it); re-encode mode does not.
 *
 * Kept side-by-side with [FfmpegCutEngine] for reference/debugging even though it lost the
 * cut-engine bake-off (see notes/results.md) — it reliably fails the metadata-preservation
 * contract in both modes (drops all `com.android.*`/`com.xiaomi.*` tags, overwrites
 * `creation_time`), which is useful to have as runnable, re-checkable evidence rather than
 * only a written record.
 */
class Media3CutEngine(private val context: Context = TestFixtures.appContext()) : CutEngine {
    override val name = "media3-transformer"

    /** Set after each cut so callers can inspect which conversion path Media3 actually
     * took (transmux vs transcode) — task 3.4. */
    var lastExportResult: ExportResult? = null
        private set

    override fun losslessCut(source: File, startMs: Long, durationMs: Long, output: File) =
        run(source, startMs, durationMs, output, trimOptimization = true)

    override fun reencodeCut(source: File, startMs: Long, durationMs: Long, output: File) =
        run(source, startMs, durationMs, output, trimOptimization = false)

    private fun run(source: File, startMs: Long, durationMs: Long, output: File, trimOptimization: Boolean) {
        if (output.exists()) check(output.delete()) { "could not clear stale output ${output.name}" }

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(startMs + durationMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(source))
            .setClippingConfiguration(clipping)
            .build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        val latch = CountDownLatch(1)
        var exportException: ExportException? = null
        var result: ExportResult? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val transformer = Transformer.Builder(context)
                .experimentalSetTrimOptimizationEnabled(trimOptimization)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        result = exportResult
                        latch.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException,
                    ) {
                        exportException = exception
                        result = exportResult
                        latch.countDown()
                    }
                })
                .build()
            transformer.start(editedMediaItem, output.absolutePath)
        }

        check(latch.await(60, TimeUnit.SECONDS)) { "Media3 export timed out for ${output.name}" }
        lastExportResult = result
        exportException?.let { throw it }
    }
}
