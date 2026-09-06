package dev.tagalong.app

import android.Manifest
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import dev.tagalong.engine.MetadataReader
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end instrumented test: seeds the fixture into MediaStore, launches the app,
 * interacts with the system photo picker via UIAutomator, triggers the cut, finds the
 * output by its predictable display name, and asserts metadata preservation.
 *
 * Design D3: seed → launch → UIAutomator picker → Compose → MediaStore probe.
 */
@RunWith(AndroidJUnit4::class)
class E2eCutTest {

    companion object {
        private const val WAIT_PICKER_MS = 30_000L
        private const val WAIT_CUT_MS = 60_000L
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** The app requests this before opening ACTION_OPEN_DOCUMENT so picked bytes retain GPS tags. */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )

    /** Target-app context — used for ContentResolver and cacheDir. */
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Instrumentation (test-APK) context — assets in `androidTest/assets/` live here,
     * not in the target app's APK.
     */
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var samples: List<TestSamples.SampleVideo> = emptyList()
    private var seededUri: Uri? = null
    private var outputUri: Uri? = null

    @Before
    fun setUp() {
        samples = TestSamples.discover(testContext)
    }

    @After
    fun tearDown() {
        samples.forEach(::cleanupSample)
    }

    @Test
    fun fullFlow_preservesMetadata() {
        val failures = mutableListOf<String>()

        samples.forEachIndexed { index, sample ->
            try {
                runSample(sample)
            } catch (failure: Throwable) {
                failures += "${sample.fileName}: ${failure.message ?: failure::class.java.simpleName}"
            } finally {
                if (index < samples.lastIndex) {
                    runCatching { returnToHome() }
                        .onFailure { resetFailure ->
                            failures += "${sample.fileName}: could not reset app for next sample: " +
                                (resetFailure.message ?: resetFailure::class.java.simpleName)
                        }
                }
                cleanupSample(sample)
            }
        }

        assertTrue(
            "Sample end-to-end failures: ${failures.joinToString("; ")}",
            failures.isEmpty(),
        )
    }

    private fun runSample(sample: TestSamples.SampleVideo) {
        val label = "[${sample.fileName}]"
        cleanupSample(sample)
        seededUri = MediaStoreSeeder.insert(context, sample.fileName, testContext)

        // 1. Materialize the selected sample to a unique cache path for FFprobe.
        val sourceFile = TestSamples.materialize(context, testContext, sample)
        val sourceProbe = MetadataReader.probe(sourceFile)

        // 2. Click "Pick video" in the app.
        composeTestRule.onNodeWithText("Pick video").performClick()

        // 3. Select this sample by its discovered filename, never by picker ordering.
        FilePickerRobot.selectItem(device, sample)

        // 4. Wait for "Cut and save" to appear (picker accepted; ViewModel loaded the source).
        composeTestRule.waitUntil(WAIT_PICKER_MS) {
            composeTestRule.onAllNodesWithText("Cut and save").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Record the cut start time so we can filter MediaStore by date_added later.
        val cutStartEpochSeconds = System.currentTimeMillis() / 1000

        // 6. Click "Cut and save".
        composeTestRule.onNodeWithText("Cut and save").performClick()

        // 7. Wait for the result screen (ffmpeg-kit + swiftshader can be slow).
        composeTestRule.waitUntil(WAIT_CUT_MS) {
            composeTestRule.onAllNodesWithText("Cut result").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. Locate this sample's output, not another sample's or a stale generic output.
        val foundUri = MediaStoreSeeder.findRecentlyAdded(
            context,
            afterEpochSeconds = cutStartEpochSeconds - 1,
            nameSubstring = "${sample.stem}_from_",
        )
        assertNotNull(
            "$label no output containing '${sample.stem}_from_' was found after the cut",
            foundUri,
        )
        outputUri = foundUri

        val outputDisplayName = MediaStoreSeeder.getDisplayName(context, foundUri!!)
        assertNotNull("$label could not read display name of output", outputDisplayName)
        assertTrue(
            "$label output name should contain '_to_': $outputDisplayName",
            outputDisplayName!!.contains("_to_"),
        )
        assertTrue(
            "$label output name should end with .mp4: $outputDisplayName",
            outputDisplayName.endsWith(".mp4"),
        )

        // 9. Materialize the output with a sample-specific path for FFprobe.
        val outputFile = File(context.cacheDir, "probe-output-${sample.fileName}")
        context.contentResolver.openInputStream(foundUri)?.use { input ->
            outputFile.outputStream().use { out -> input.copyTo(out) }
        } ?: error("$label could not open output from MediaStore: $foundUri")
        val outputProbe = MetadataReader.probe(outputFile)

        // 10. All source format tags must be present in the output unchanged — including GPS.
        val formatDiff = MetadataAssertions.sourceTagsSubsetOfOutput(sourceProbe.formatTags, outputProbe.formatTags)
        assertTrue(
            "$label format tags lost or changed: missing=${formatDiff.missing} changed=${formatDiff.changed}",
            formatDiff.isSubset,
        )

        // 11. Spot-check creation_time — the most critical tag (drives gallery date).
        val srcCreation = sourceProbe.formatTags["creation_time"]
        val outCreation = outputProbe.formatTags["creation_time"]
        assertTrue(
            "$label creation_time must match source ($srcCreation vs $outCreation)",
            srcCreation == outCreation,
        )
    }

    private fun cleanupSample(sample: TestSamples.SampleVideo) {
        MediaStoreSeeder.delete(context, seededUri, outputUri)
        MediaStoreSeeder.deleteByDisplayNameContains(context, "${sample.stem}_from_")
        TestSamples.deleteMaterialized(context, sample)
        File(context.cacheDir, "input.mp4").delete()
        context.cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("cut-") && it.name.endsWith(".mp4") }
            .forEach(File::delete)
        seededUri = null
        outputUri = null
    }

    private fun returnToHome() {
        FilePickerRobot.dismissIfOpen(device)
        waitForAnyComposeText("Cut result", "Pick a different video", "Pick video")

        when {
            hasComposeText("Cut result") -> {
                composeTestRule.onNodeWithContentDescription("Back to trim").performClick()
                waitForComposeText("Pick a different video")
            }
            hasComposeText("Pick a different video") -> Unit
            hasComposeText("Pick video") -> return
        }

        composeTestRule.onNodeWithText("Pick a different video").performClick()
        waitForComposeText("Pick video")
    }

    /** Compose can have no hierarchy for a short interval while DocumentsUI is dismissed. */
    private fun hasComposeText(text: String): Boolean = runCatching {
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    private fun waitForComposeText(text: String) {
        composeTestRule.waitUntil(WAIT_PICKER_MS) { hasComposeText(text) }
    }

    private fun waitForAnyComposeText(vararg texts: String) {
        composeTestRule.waitUntil(WAIT_PICKER_MS) { texts.any(::hasComposeText) }
    }

}
