package dev.tagalong.app

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the launcher half of `launcher-identity`: the app must present its own
 * adaptive mark on system surfaces rather than the platform default application icon.
 *
 * The defect this exists for was invisible to every other suite. The full Image Asset export was
 * committed — five density buckets, adaptive foreground, legacy rasters, background colour — and
 * shipped green anyway, because `android:icon` was never declared on `<application>`. Nothing
 * referenced the resources, so they were dead weight and the platform rendered its placeholder.
 *
 * `ApplicationInfo.icon` is the field that makes that detectable. It is populated from the
 * manifest attribute and stays `0` when the attribute is absent, so the assertion fails on the
 * specific declaration that was missing — no launcher, no screenshot, no rendering involved.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIdentityTest {

    /** Target-app context, per the convention in E2eCutTest. */
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manifest_declaresTheLauncherIcon() {
        // `applicationInfo` rather than `packageManager.getApplicationInfo(pkg, 0)`: the latter is
        // deprecated on API 33+, and its replacement overload does not exist at this app's
        // minSdk 31, so avoiding it is cheaper than a version branch. Same underlying value.
        assertResolvesTo(
            resId = context.applicationInfo.icon,
            expectedEntry = "ic_launcher",
            attributeName = "android:icon",
        )

        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(
            "The application icon is ${icon::class.java.name}, not an AdaptiveIconDrawable. " +
                "Expected res/mipmap-anydpi-v26/ic_launcher.xml to inflate an adaptive icon, so " +
                "that launcher masks control the visible region.",
            icon is AdaptiveIconDrawable,
        )
    }

    /**
     * Asserts [resId] was declared and points at the resource entry it is supposed to, not merely
     * that something was declared.
     *
     * The entry-name check is what gives this teeth. An id-only check would accept any non-zero
     * resource, so a copy-paste that pointed `android:icon` at the round variant, or at some other
     * drawable, would pass while the app shipped the wrong thing.
     *
     * Ordering matters here, and it is load-bearing for the `AdaptiveIconDrawable` check in the
     * test above. Asserting that the app icon "is an AdaptiveIconDrawable" on its own looks
     * reasonable and is worthless: with `android:icon` absent, `getApplicationIcon()` returns the
     * platform default app icon, which is itself an AdaptiveIconDrawable. That version of this
     * assertion was written, and it passed against the unfixed tree. It only means something
     * because the resource identity has already been pinned to this app's own `ic_launcher`.
     */
    private fun assertResolvesTo(resId: Int, expectedEntry: String, attributeName: String) {
        assertTrue(
            "$attributeName is not declared on <application>: ApplicationInfo resolves it to 0, " +
                "so the surface that uses it renders the platform default app icon. Expected a " +
                "resource id for @mipmap/$expectedEntry.",
            resId != 0,
        )

        val entry = runCatching { context.resources.getResourceEntryName(resId) }.getOrNull()
        assertTrue(
            "$attributeName resolves to resource entry '$entry', expected '$expectedEntry'.",
            entry == expectedEntry,
        )
    }

    /*
     * DELIBERATELY ABSENT: an assertion that no monochrome layer is declared.
     *
     * `AdaptiveIconDrawable.getMono()` (API 33) looks like the obvious way to pin this down, and
     * it is the natural thing to add here later. Do not add it without probing it first: what it
     * returns when an adaptive icon declares *no* `monochrome` element is unverified, and it may
     * hand back a non-null drawable that just draws the foreground. An assertion written against
     * that guess would pass for the wrong reason, which is worse than having no coverage at all —
     * it would read as a guard while enforcing nothing.
     *
     * The absence of the layer is asserted at the level the spec states it, by reading the two
     * adaptive-icon XMLs, and the reason it must stay absent is recorded beside `android:icon` in
     * AndroidManifest.xml. See openspec/changes/add-launcher-icon/design.md, Decisions 1 and 5.
     *
     *
     * DELIBERATELY ABSENT: an assertion for `android:roundIcon`.
     *
     * `PackageItemInfo.icon` is the only public icon field — `ApplicationInfo` exposes no
     * `roundIcon` (confirmed against android.jar) — and `getApplicationIcon()` folds the round
     * variant in silently with nothing observable to distinguish it. The tempting substitute,
     * `resources.getIdentifier("ic_launcher_round", ...)`, asserts only that the *file* exists,
     * which was already true while this bug was live: the whole asset export was committed. It
     * would pass against the unfixed tree.
     *
     * `android:roundIcon` is therefore verified statically, where the declaration is visible:
     * `aapt2 dump badging` does NOT report it at all (verified: zero matches), but
     * `aapt2 dump xmltree --file AndroidManifest.xml <apk>` shows both attributes with their
     * resolved resource ids. See tasks 1.1 and 5.2.
     */
}
