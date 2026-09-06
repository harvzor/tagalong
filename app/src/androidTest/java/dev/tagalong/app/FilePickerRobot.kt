package dev.tagalong.app

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * UIAutomator interactions for the system file picker (ACTION_OPEN_DOCUMENT / DocumentsUI).
 *
 * On the Pixel_7_API_34 emulator the picker runs as `com.google.android.documentsui` and
 * shows files in a `GridView` (resource id `dir_list`). Each item is a clickable CardView
 * (`item_root`); the filename is in a child `android:id/title` TextView.
 *
 * Widget tree confirmed via `adb shell uiautomator dump` on Pixel_7_API_34 (API 34,
 * swiftshader emulator) while the ACTION_OPEN_DOCUMENT chooser was open showing Recent files.
 *
 * Known fragility: DocumentsUI layout can differ by OEM or Android version. Keeping this
 * class isolated means updates are contained if a different device image is targeted.
 */
object FilePickerRobot {

    // GMS and AOSP images use different DocumentsUI package names. Resolve the active one
    // before constructing resource selectors instead of assuming one system implementation.
    private val docsPackages = listOf(
        "com.google.android.documentsui",
        "com.android.documentsui",
    )
    private const val APP_PKG = "dev.tagalong.app"

    private const val WAIT_PICKER_MS = 15_000L

    /**
     * Waits for the DocumentsUI file chooser, then selects exactly one item for [sample].
     * The full filename is preferred; the filename stem is accepted only when it identifies
     * exactly one visible item. A missing or ambiguous match fails instead of selecting an
     * arbitrary file.
     *
     * Call from a test after an action that opens an ACTION_OPEN_DOCUMENT chooser.
     */
    fun selectItem(device: UiDevice, sample: TestSamples.SampleVideo) {
        // 1. Wait for DocumentsUI process to become foreground and resolve its package.
        val docsPackage = docsPackages.firstOrNull { packageName ->
            device.wait(Until.hasObject(By.pkg(packageName)), WAIT_PICKER_MS)
        } ?: error("DocumentsUI did not appear within ${WAIT_PICKER_MS}ms")
        val itemRootRes = "$docsPackage:id/item_root"
        val dirListRes = "$docsPackage:id/dir_list"

        // 2. Wait for the file list to populate.
        device.wait(Until.findObject(By.res(dirListRes)), WAIT_PICKER_MS)
            ?: error("DocumentsUI file list ($dirListRes) not found. " +
                "Run `adb shell uiautomator dump` while the picker is open and " +
                "update FilePickerRobot for this device image.")

        // 3. DocumentsUI remembers its last directory. Search by the current sample's
        // full filename so a prior directory choice cannot hide a valid seeded row.
        val searchButton = device.findObject(By.res("$docsPackage:id/option_menu_search"))
            ?: device.findObject(By.desc("Search"))
        if (searchButton != null) {
            searchButton.click()
            val searchField = device.wait(
                Until.findObject(By.res("$docsPackage:id/search_src_text")),
                WAIT_PICKER_MS,
            ) ?: error("[${sample.fileName}] DocumentsUI search field did not appear")
            searchField.setText(sample.fileName)
        }

        // 4. Search text itself also contains the filename, so match actual file cards rather
        // than raw text nodes. This prevents the query field from being counted as a duplicate.
        val byExactCard = By.res(itemRootRes).hasDescendant(By.text(sample.fileName))
        val byStemCard = By.res(itemRootRes).hasDescendant(By.textContains(sample.stem))
        if (!device.wait(Until.hasObject(byStemCard), WAIT_PICKER_MS)) {
            error("[${sample.fileName}] no matching item found in DocumentsUI search results; " +
                "expected the seeded filename or unique stem")
        }

        val exactMatches = device.findObjects(byExactCard)
        when {
            exactMatches.size == 1 -> {
                exactMatches.single().click()
                return
            }
            exactMatches.size > 1 -> error(
                "[${sample.fileName}] file picker returned ${exactMatches.size} exact file cards"
            )
        }

        // 5. Some DocumentsUI variants truncate or omit the extension in the visible title.
        // Accept a stem match only when it is unambiguous.
        val stemMatches = device.findObjects(byStemCard)
        when (stemMatches.size) {
            1 -> stemMatches.single().click()
            0 -> error(
                "[${sample.fileName}] no matching item found in DocumentsUI search results; " +
                    "expected the seeded filename or unique stem"
            )
            else -> error(
                "[${sample.fileName}] file picker selection is ambiguous: " +
                    "${stemMatches.size} file cards match stem '${sample.stem}'"
            )
        }
    }

    /** Dismisses DocumentsUI after a failed selection so the next sample starts in the app. */
    fun dismissIfOpen(device: UiDevice) {
        if (docsPackages.any { device.findObject(By.pkg(it)) != null }) {
            device.pressBack()
            device.wait(Until.hasObject(By.pkg(APP_PKG)), WAIT_PICKER_MS)
            device.waitForIdle()
        }
    }
}
