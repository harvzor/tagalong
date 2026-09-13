package dev.tagalong.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.tagalong.engine.MetadataReader
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Share-intake instrumented tests (change add-share-target, specs share-intake).
 *
 * These launch MainActivity directly with a crafted ACTION_SEND intent — no share-sheet
 * UIAutomator dance: the sheet's job is to deliver exactly this intent, and a direct launch
 * also exercises paths the sheet won't (missing EXTRA_STREAM). MediaStore row URIs are the
 * canonical gallery-sender shape; FileProvider senders are covered by the on-device matrix
 * in tasks §4, not here.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntakeTest {

    companion object {
        private const val WAIT_MS = 30_000L
    }

    /** No activity rule: these tests launch with a custom intent via ActivityScenario. */
    @get:Rule
    val compose = createEmptyComposeRule()

    /** 3.3 asserts unredacted bytes, which requires the grant (same rule shape as E2eCutTest). */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private var scenario: ActivityScenario<MainActivity>? = null
    private var seededUri: Uri? = null

    @After
    fun tearDown() {
        // close() finishes the activity so the next singleTask launch starts a fresh
        // onCreate intake rather than an onNewIntent into a leftover session.
        scenario?.close()
        scenario = null
        MediaStoreSeeder.delete(context, seededUri)
        seededUri = null
        File(context.cacheDir, "input.mp4").delete()
    }

    private fun launchShare(uri: Uri?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClassName(context.packageName, MainActivity::class.java.name)
        }
        scenario = ActivityScenario.launch(intent)
    }

    private fun hasText(text: String, substring: Boolean = false): Boolean = runCatching {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(WAIT_MS) { hasText(text, substring) }
    }

    /** Seeds a corpus sample into MediaStore and returns (sample, row uri). */
    private fun seedSample(): Pair<TestSamples.SampleVideo, Uri> {
        val sample = TestSamples.discover(testContext).first()
        return sample to MediaStoreSeeder.insert(context, sample.fileName, testContext)
    }

    /** 3.1 — media share lands on Trim with the source loaded; Home is never displayed. */
    @Test
    fun sharedVideo_opensTrimDirectlyWithoutHome() {
        val (sample, uri) = seedSample()
        seededUri = uri
        launchShare(uri)

        // Trim's primary action appears only once the intake copy + probe completed.
        waitText("Cut and save")
        // The path label carries the seeded file's name — the loaded source is this video.
        waitText(sample.fileName, substring = true)
        // Home never composed on this session (startDestination is trim).
        assertTrue(
            "Home screen must not be displayed on a share launch",
            !hasText("Pick video"),
        )
    }

    /** 3.2 — an ACTION_SEND with no usable payload is an ordinary Home launch, no error. */
    @Test
    fun unusableShare_fallsBackToHomeSilently() {
        launchShare(null)
        waitText("Pick video")
        assertTrue(
            "Unusable share must not navigate to Trim",
            !hasText("Cut and save"),
        )
    }

    /**
     * 3.3 — a media-provider share with ACCESS_MEDIA_LOCATION granted materialises the
     * unredacted bytes: every format tag of the corpus asset — GPS location included —
     * arrives in the cache file the engine will read, unchanged (setRequireOriginal branch;
     * plain openInputStream would have handed back framework-redacted bytes).
     */
    @Test
    fun sharedVideo_materialisesUnredactedBytesWithLocation() {
        val (sample, uri) = seedSample()
        seededUri = uri

        val sourceFile = TestSamples.materialize(context, testContext, sample)
        val sourceProbe = MetadataReader.probe(sourceFile)
        assertTrue(
            "corpus sample ${sample.fileName} must carry a location representation",
            sourceProbe.locationRepresentation.hasQuickTime ||
                sourceProbe.locationRepresentation.hasGenericMdta,
        )

        launchShare(uri)
        waitText("Cut and save")

        val cacheFile = File(context.cacheDir, "input.mp4")
        assertTrue("intake cache file missing: ${cacheFile.path}", cacheFile.exists())
        val cachedProbe = MetadataReader.probe(cacheFile)

        val diff = MetadataAssertions.sourceTagsSubsetOfOutput(
            sourceProbe.formatTags, cachedProbe.formatTags,
        )
        assertTrue(
            "format tags lost/redacted between share Uri and cache bytes: " +
                "missing=${diff.missing} changed=${diff.changed}",
            diff.isSubset,
        )
        assertTrue(
            "GPS location was redacted from the shared byte stream",
            cachedProbe.locationRepresentation.hasQuickTime ||
                cachedProbe.locationRepresentation.hasGenericMdta,
        )
    }
}
