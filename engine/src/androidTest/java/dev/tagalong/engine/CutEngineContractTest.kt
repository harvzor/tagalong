package dev.tagalong.engine

import android.content.Context
import android.media.MediaExtractor
import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant

/**
 * The shared bake-off contract (D1): every requirement in
 * specs/metadata-preserving-cut/spec.md, run identically against whichever [CutEngine] a
 * subclass provides. One subclass per arm — deleting the losing arm later (task 5.4) means
 * deleting one subclass + its impl file, nothing here.
 */
abstract class CutEngineContractTest {
    companion object {
        private const val TAG = "CutEngineContract"
    }

    abstract fun engine(): CutEngine

    private lateinit var context: Context
    private lateinit var samples: List<TestFixtures.SampleVideo>

    @Before
    fun setUp() {
        context = TestFixtures.appContext()
        samples = TestFixtures.samples(context)
    }

    @Test
    fun losslessCut_preservesAllSourceTags() = runContract(CutMode.LOSSLESS)

    @Test
    fun reencodeCut_preservesAllSourceTagsIdentically() = runContract(CutMode.REENCODE)

    private fun runContract(mode: CutMode) {
        val engine = engine()
        val failures = mutableListOf<String>()

        for (sample in samples) {
            runCatching { runContractForSample(engine, sample, mode) }
                .onFailure { failure ->
                    failures += "${sample.fileName}: ${failure.message ?: failure::class.java.simpleName}"
                    Log.e(TAG, "[${engine.name}/${sample.fileName}/$mode] contract failed", failure)
                }
        }

        assertTrue(
            "[${engine.name}/$mode] sample contract failures: ${failures.joinToString("; ")}",
            failures.isEmpty(),
        )
    }

    private fun runContractForSample(
        engine: CutEngine,
        sample: TestFixtures.SampleVideo,
        mode: CutMode,
    ) {
        val label = "${engine.name}/${sample.fileName}/$mode"
        val source = TestFixtures.sourceFile(sample, context)
        val sourceProbe = MetadataReader.probe(source)
        val sourceHashBefore = FileAssertions.sha256(source)
        val (startMs, durationMs) = cutInterval(sourceProbe, label)
        val output = TestFixtures.outputFile(sample, mode, engine.name, context).apply { delete() }

        engine.cut(mode, source, startMs = startMs, durationMs = durationMs, output = output)

        // Requirement: The original file is never modified.
        assertTrue("[$label] output must exist and be distinct from source", output.exists() && output != source)
        assertEquals(
            "[$label] source bytes must be unchanged after the cut",
            sourceHashBefore, FileAssertions.sha256(source),
        )

        val outputProbe = MetadataReader.probe(output)
        assertReadableMediaSamples(output, label)
        Log.i(TAG, "[$label] source tags=${sourceProbe.formatTags} output tags=${outputProbe.formatTags}")

        // Requirement: Lossless cut preserves all source file-level metadata.
        // Requirement: The guarantee holds identically in both modes — same assertion, both modes.
        val formatDiff = MetadataAssertions.sourceTagsSubsetOfOutput(sourceProbe.formatTags, outputProbe.formatTags)
        assertTrue(
            "[$label] format tags lost or changed: missing=${formatDiff.missing} changed=${formatDiff.changed}",
            formatDiff.isSubset,
        )

        // Requirement: Creation date and location are retained.
        assertEquals("[$label] creation_time", sourceProbe.formatTags["creation_time"], outputProbe.formatTags["creation_time"])
        assertEquals("[$label] location", sourceProbe.formatTags["location"], outputProbe.formatTags["location"])

        // FFprobe normalizes QuickTime and generic metadata to the same logical key. The
        // raw representation is a separate preservation-critical assertion.
        if (sourceProbe.locationRepresentation.hasQuickTime) {
            assertTrue(
                "[$label] QuickTime ©xyz representation is missing from output",
                outputProbe.locationRepresentation.hasQuickTime,
            )
            assertTrue(
                "[$label] QuickTime ©xyz payload changed",
                sourceProbe.locationRepresentation.quickTimePayloadsEqual(outputProbe.locationRepresentation),
            )
        }
        assertTrue(
            "[$label] generic mdta location entries were lost",
            outputProbe.locationRepresentation.genericMdtaKeys.containsAll(
                sourceProbe.locationRepresentation.genericMdtaKeys,
            ),
        )

        // Requirement: Orientation is preserved as a signal, not baked into frames.
        assertEquals("[$label] rotation signal", sourceProbe.videoRotationDegrees, outputProbe.videoRotationDegrees)
        assertEquals(
            "[$label] frame dimensions must be unchanged (not baked-in rotated)",
            sourceProbe.videoWidth to sourceProbe.videoHeight,
            outputProbe.videoWidth to outputProbe.videoHeight,
        )

        // Requirement: Gallery date is preserved.
        val captureTimeMillis = Instant.parse(
            requireNotNull(sourceProbe.formatTags["creation_time"]) {
                "[$label] source has no creation_time"
            }
        ).toEpochMilli()
        val saveResult = DateTakenStore.registerAndReadBack(
            context,
            output,
            captureTimeMillis,
            displayName = "${sample.fileName}-${mode.name.lowercase()}-gallery.mp4",
        )
        assertEquals(
            "[$label] MediaStore.DATE_TAKEN must equal source capture date",
            captureTimeMillis,
            saveResult.dateTakenMillis,
        )
    }

    private fun assertReadableMediaSamples(file: File, label: String) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            assertTrue("[$label] output must contain at least one media track", extractor.trackCount > 0)
            for (track in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(track).getString("mime") ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(track)
                val sampleSize = extractor.readSampleData(ByteBuffer.allocate(2 * 1024 * 1024), 0)
                assertTrue("[$label] $mime track must expose a media sample", sampleSize > 0)
                extractor.unselectTrack(track)
            }
        } finally {
            extractor.release()
        }
    }

    private fun cutInterval(sourceProbe: MediaProbe, label: String): Pair<Long, Long> {
        val sourceDurationMs = requireNotNull(sourceProbe.durationMs) {
            "[$label] source duration is unavailable; cannot derive a valid cut interval"
        }
        val startMs = minOf(500L, sourceDurationMs / 4)
        val durationMs = minOf(3000L, sourceDurationMs - startMs)
        require(startMs > 0L && durationMs > 0L && startMs + durationMs <= sourceDurationMs) {
            "[$label] source duration ${sourceDurationMs}ms cannot provide a valid cut interval"
        }
        return startMs to durationMs
    }
}
