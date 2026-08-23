package dev.tagalong.engine

import android.content.Context
import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
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
        private const val START_MS = 500L
        private const val DURATION_MS = 3000L
    }

    abstract fun engine(): CutEngine

    private lateinit var context: Context
    private lateinit var source: File
    private lateinit var sourceProbe: MediaProbe
    private lateinit var sourceHashBefore: String

    @Before
    fun setUp() {
        context = TestFixtures.appContext()
        source = TestFixtures.sourceFile(context)
        sourceProbe = MetadataReader.probe(source)
        sourceHashBefore = FileAssertions.sha256(source)
    }

    @Test
    fun losslessCut_preservesAllSourceTags() = runContract(CutMode.LOSSLESS)

    @Test
    fun reencodeCut_preservesAllSourceTagsIdentically() = runContract(CutMode.REENCODE)

    private fun runContract(mode: CutMode) {
        val engine = engine()
        val label = "${engine.name}/$mode"
        val output = TestFixtures.outputFile(context, "${engine.name}-${mode.name.lowercase()}.mp4")

        engine.cut(mode, source, startMs = START_MS, durationMs = DURATION_MS, output = output)

        // Requirement: The original file is never modified.
        assertTrue("[$label] output must exist and be distinct from source", output.exists() && output != source)
        assertEquals(
            "[$label] source bytes must be unchanged after the cut",
            sourceHashBefore, FileAssertions.sha256(source),
        )

        val outputProbe = MetadataReader.probe(output)
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

        // Requirement: Orientation is preserved as a signal, not baked into frames.
        assertEquals("[$label] rotation signal", sourceProbe.videoRotationDegrees, outputProbe.videoRotationDegrees)
        assertEquals(
            "[$label] frame dimensions must be unchanged (not baked-in rotated)",
            sourceProbe.videoWidth to sourceProbe.videoHeight,
            outputProbe.videoWidth to outputProbe.videoHeight,
        )

        // Requirement: Gallery date is preserved.
        val captureTimeMillis = Instant.parse(
            requireNotNull(sourceProbe.formatTags["creation_time"]) { "source has no creation_time" }
        ).toEpochMilli()
        val saveResult = DateTakenStore.registerAndReadBack(context, output, captureTimeMillis)
        assertEquals("[$label] MediaStore.DATE_TAKEN must equal source capture date", captureTimeMillis, saveResult.dateTakenMillis)
    }
}
