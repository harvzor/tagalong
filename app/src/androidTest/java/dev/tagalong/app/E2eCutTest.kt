package dev.tagalong.app

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        private const val FIXTURE_ASSET = "xiaomi-poco-x5.mp4"
        private const val WAIT_PICKER_MS = 30_000L
        private const val WAIT_CUT_MS = 60_000L
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Target-app context — used for ContentResolver and cacheDir. */
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Instrumentation (test-APK) context — assets in `androidTest/assets/` live here,
     * not in the target app's APK.
     */
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var seededUri: Uri? = null
    private var outputUri: Uri? = null

    @Before
    fun setUp() {
        seededUri = MediaStoreSeeder.insert(context, FIXTURE_ASSET, testContext)
    }

    @After
    fun tearDown() {
        MediaStoreSeeder.delete(context, seededUri, outputUri)
        // Clean up cached probe files
        File(context.cacheDir, FIXTURE_ASSET).delete()
        File(context.cacheDir, "source-via-cr.mp4").delete()
        File(context.cacheDir, "probe-output.mp4").delete()
    }

    @Test
    fun fullFlow_preservesMetadata() {
        // 1. Copy the fixture asset to cacheDir so MetadataReader (FFprobe) can open it by path.
        //    Assets in androidTest/assets/ live in the instrumentation APK (testContext).
        val sourceViaCr = File(context.cacheDir, "source-via-cr.mp4")
        testContext.assets.open(FIXTURE_ASSET).use { input ->
            sourceViaCr.outputStream().use { out -> input.copyTo(out) }
        }
        val sourceProbe = MetadataReader.probe(sourceViaCr)

        // 2. Click "Pick video" in the app
        composeTestRule.onNodeWithText("Pick video").performClick()

        // 3. Drive the system photo picker with UIAutomator
        PhotoPickerRobot.selectFirstItem(device)

        // 4. Wait for "Cut and save" to appear (picker accepted; ViewModel loaded the source)
        composeTestRule.waitUntil(WAIT_PICKER_MS) {
            composeTestRule.onAllNodesWithText("Cut and save").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Record the cut start time so we can filter MediaStore by date_added later
        val cutStartEpochSeconds = System.currentTimeMillis() / 1000

        // 6. Click "Cut and save"
        composeTestRule.onNodeWithText("Cut and save").performClick()

        // 7. Wait for "Saved" status (generous timeout — ffmpeg-kit + swiftshader can be slow)
        composeTestRule.waitUntil(WAIT_CUT_MS) {
            composeTestRule.onAllNodesWithText("Saved", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 8. Locate the output in MediaStore.
        //    The Google Photopicker (com.google.android.providers.media.module) may return its
        //    internal picker_id as the DISPLAY_NAME on the picked URI rather than the actual
        //    filename — so we can't predict the exact base name.  Instead, search for a video
        //    added AFTER the cut started whose name contains "_from_" (always present in our
        //    output naming scheme, never in the seeded file's name).
        val foundUri = MediaStoreSeeder.findRecentlyAdded(
            context,
            afterEpochSeconds = cutStartEpochSeconds - 1, // small buffer for clock skew
            nameSubstring = "_from_",
        )
        assertNotNull(
            "No output video with '_from_' in its name was found in MediaStore after the cut",
            foundUri,
        )
        outputUri = foundUri

        // Verify the output display name follows the expected format
        val outputDisplayName = MediaStoreSeeder.getDisplayName(context, foundUri!!)
        assertNotNull("Could not read display name of output", outputDisplayName)
        assertTrue(
            "Output name should contain '_to_': $outputDisplayName",
            outputDisplayName!!.contains("_to_"),
        )
        assertTrue(
            "Output name should end with .mp4: $outputDisplayName",
            outputDisplayName.endsWith(".mp4"),
        )

        // 9. Materialize the output to cache so MetadataReader (FFprobe) can probe it by file path
        val outputFile = File(context.cacheDir, "probe-output.mp4")
        context.contentResolver.openInputStream(foundUri)?.use { input ->
            outputFile.outputStream().use { out -> input.copyTo(out) }
        } ?: error("Could not open output from MediaStore: $foundUri")
        val outputProbe = MetadataReader.probe(outputFile)

        // 10. All source format tags (except those the picker path strips) must be present in the
        //     output unchanged.
        //
        //     KNOWN GAP (see CLAUDE.md "Location tag silently dropped on pick"):
        //     The Google Photopicker (com.google.android.providers.media.module) strips GPS
        //     container tags (`location`, `location-eng`) from the byte stream it exposes via
        //     openInputStream, regardless of ACCESS_MEDIA_LOCATION — this is a picker-side
        //     redaction that the engine cannot overcome. The engine preserves location fine
        //     when given direct file access; this limitation is in the pick step, not the cut.
        //     Exclude those tags so the test doesn't fail for something the engine didn't break.
        val pickerRedactedTags = setOf("location", "location-eng")
        val tagsToCheck = sourceProbe.formatTags.filterKeys { it !in pickerRedactedTags }
        val formatDiff = MetadataAssertions.sourceTagsSubsetOfOutput(tagsToCheck, outputProbe.formatTags)
        assertTrue(
            "Format tags lost or changed: missing=${formatDiff.missing} changed=${formatDiff.changed}",
            formatDiff.isSubset,
        )

        // 11. Spot-check creation_time — the most critical tag (drives gallery date)
        val srcCreation = sourceProbe.formatTags["creation_time"]
        val outCreation = outputProbe.formatTags["creation_time"]
        assertTrue("creation_time must match source ($srcCreation vs $outCreation)", srcCreation == outCreation)
    }

}
