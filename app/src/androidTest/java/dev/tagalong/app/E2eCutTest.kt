package dev.tagalong.app

import android.Manifest
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        private const val FIXTURE_ASSET = "xiaomi-poco-x5.mp4"
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

        // 3. Drive the system file picker (ACTION_OPEN_DOCUMENT) with UIAutomator
        FilePickerRobot.selectItem(device)

        // 4. Wait for "Cut and save" to appear (picker accepted; ViewModel loaded the source)
        composeTestRule.waitUntil(WAIT_PICKER_MS) {
            composeTestRule.onAllNodesWithText("Cut and save").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Record the cut start time so we can filter MediaStore by date_added later
        val cutStartEpochSeconds = System.currentTimeMillis() / 1000

        // 6. Click "Cut and save"
        composeTestRule.onNodeWithText("Cut and save").performClick()

        // 7. Wait for the result screen (generous timeout — ffmpeg-kit + swiftshader can be slow).
        //    A successful cut navigates directly to ResultScreen; it no longer renders a "Saved"
        //    status row on the trim screen.
        composeTestRule.waitUntil(WAIT_CUT_MS) {
            composeTestRule.onAllNodesWithText("Cut result").fetchSemanticsNodes().isNotEmpty()
        }

        // 8. Locate the output in MediaStore by the "_from_" substring that is always present
        //    in the output naming scheme and never in the seeded file's name.
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

        // 10. All source format tags must be present in the output unchanged — including GPS.
        //     ACTION_OPEN_DOCUMENT provides an unredacted byte stream, so -map_metadata 0
        //     copies location tags and all other format tags automatically.
        val formatDiff = MetadataAssertions.sourceTagsSubsetOfOutput(sourceProbe.formatTags, outputProbe.formatTags)
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
