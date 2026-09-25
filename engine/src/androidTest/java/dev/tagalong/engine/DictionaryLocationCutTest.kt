package dev.tagalong.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Regression guard for the non-QuickTime location branch of change `cut-command-honesty`
 * (design D2, task 3.4).
 *
 * The engine suppresses ffmpeg's location mistranslation ONLY when the source carries a
 * QuickTime `©xyz` the finalizer can restore. For a source whose location lives purely in
 * the normalized location dictionary (no `©xyz`), there is no finalizer to bring the value
 * back, so the engine MUST NOT suppress it — the source's location value has to survive the
 * cut unchanged.
 *
 * No committed fixture demonstrates this source shape (the corpus is QuickTime-`©xyz`
 * camera files), and a loci-free, dictionary-only MP4 is not something the bundled ffmpeg
 * can even emit, so the dictionary-shape source is built at runtime: a raw stream-copy cut
 * of a real sample. FFmpeg's mov muxer never writes a `©xyz` for MP4, so that raw cut is a
 * genuine "location as a dictionary entry only, no ©xyz" file — the exact shape the
 * non-suppression branch exists to protect.
 *
 * This is deliberately a separate, self-contained test rather than a file under
 * sample-videos/: the corpus is documented as device-originated samples and every file there
 * flows into both modules' full preservation matrix, which a synthetic would not fit.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryLocationCutTest {

    private lateinit var context: android.content.Context
    private lateinit var samples: List<TestFixtures.SampleVideo>

    @Before
    fun setUp() {
        context = TestFixtures.appContext()
        samples = TestFixtures.samples(context)
    }

    @Test
    fun dictionaryLocationSourceKeepsItsLocationInBothModes() {
        val failures = mutableListOf<String>()
        for (sample in samples) {
            for (mode in CutMode.entries) {
                runCatching { assertLocationSurvivesWithoutQuickTime(sample, mode) }
                    .onFailure { failure ->
                        failures += "${sample.fileName}/$mode: ${failure.message ?: failure::class.java.simpleName}"
                    }
            }
        }
        assertTrue("dictionary-location regression failures: ${failures.joinToString("; ")}", failures.isEmpty())
    }

    private fun assertLocationSurvivesWithoutQuickTime(sample: TestFixtures.SampleVideo, mode: CutMode) {
        val label = "${sample.fileName}/$mode"
        val base = TestFixtures.sourceFile(sample, context)

        // 1. Build a dictionary-shape source: a raw lossless cut (no finalizer, current
        //    metadata mapping), which carries location as a dictionary entry and never as ©xyz.
        val dictSource = File(context.cacheDir, "tagalong-dictloc/${sample.stem}-dict-source.mp4")
            .also { it.parentFile?.mkdirs(); it.delete() }
        val build = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y",
                "-i", base.absolutePath, "-t", "3.000",
                "-c", "copy",
                "-map_metadata", "0",
                "-movflags", "+faststart+use_metadata_tags",
                dictSource.absolutePath,
            ),
        )
        check(ReturnCode.isSuccess(build.returnCode)) { "could not build dictionary-shape source: ${build.allLogsAsString}" }

        // 2. Confirm the source really is the non-QuickTime shape we intend to protect.
        val sourceInfo = Mp4LocationMetadata.inspect(dictSource)
        assertFalse(
            "[$label] constructed source unexpectedly carries a QuickTime ©xyz; cannot exercise the non-suppression branch",
            sourceInfo.hasQuickTime,
        )
        assertTrue(
            "[$label] constructed source carries no location dictionary entry; cannot exercise the non-suppression branch",
            sourceInfo.hasGenericMdta,
        )
        val sourceLocation = MetadataReader.probe(dictSource).formatTags["location"]
        assertTrue("[$label] constructed source must expose a normalized location value", sourceLocation != null)

        // 3. Cut it through the PRODUCTION engine. Because hasQuickTime is false, the engine
        //    must NOT suppress location; the value has to survive.
        val output = TestFixtures.outputFile(sample, mode, "dictloc", context).apply { delete() }
        FfmpegCutEngine().cut(mode, dictSource, startMs = 0, durationMs = 2000, output = output)

        // 4. The location dictionary value must survive intact.
        val outputInfo = Mp4LocationMetadata.inspect(output)
        assertTrue(
            "[$label] non-QuickTime source lost its location dictionary entry (source=${sourceInfo.genericMdtaKeys}, output=${outputInfo.genericMdtaKeys})",
            outputInfo.genericMdtaKeys.containsAll(sourceInfo.genericMdtaKeys),
        )
        assertEquals(
            "[$label] non-QuickTime source location value changed",
            sourceLocation,
            MetadataReader.probe(output).formatTags["location"],
        )
    }
}
