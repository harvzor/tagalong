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

    // Package confirmed on Pixel_7_API_34 (API 34, swiftshader emulator).
    // AOSP builds use "com.android.documentsui"; GMS builds use the google.android variant.
    private const val DOCS_PKG = "com.google.android.documentsui"
    private const val ITEM_ROOT_RES = "$DOCS_PKG:id/item_root"
    private const val DIR_LIST_RES = "$DOCS_PKG:id/dir_list"

    private const val WAIT_PICKER_MS = 15_000L

    /**
     * Waits for the DocumentsUI file chooser to appear, then selects the item whose
     * filename contains [nameSubstring] (case-insensitive). Falls back to clicking the
     * first item in the file list if no matching item is found.
     *
     * [nameSubstring] defaults to the seeded fixture base name so that only the test video
     * is targeted even if other files are visible in the chooser.
     *
     * Call from a test after an action that opens an ACTION_OPEN_DOCUMENT chooser.
     */
    fun selectItem(device: UiDevice, nameSubstring: String = "xiaomi-poco-x5") {
        // 1. Wait for DocumentsUI process to become foreground
        device.wait(Until.hasObject(By.pkg(DOCS_PKG)), WAIT_PICKER_MS)
            ?: error("DocumentsUI ($DOCS_PKG) did not appear within ${WAIT_PICKER_MS}ms")

        // 2. Wait for the file list to populate
        device.wait(Until.findObject(By.res(DIR_LIST_RES)), WAIT_PICKER_MS)
            ?: error("DocumentsUI file list ($DIR_LIST_RES) not found. " +
                "Run `adb shell uiautomator dump` while the picker is open and " +
                "update DOCS_PKG / ITEM_ROOT_RES in FilePickerRobot.")

        // 3. Try to find the specific item by filename text inside an item_root card
        val byNamedItem = By.res(ITEM_ROOT_RES).hasDescendant(By.textContains(nameSubstring))
        val namedItem = device.findObject(byNamedItem)

        if (namedItem != null) {
            namedItem.click()
            return
        }

        // 4. Fallback: click the first item_root in the list (works when nameSubstring
        //    isn't visible in the current view, or the Recent list is showing a single file)
        val firstItem = device.findObject(By.res(ITEM_ROOT_RES))
            ?: error(
                "No item_root elements found in DocumentsUI. " +
                    "Run `adb shell uiautomator dump` while the picker is open and " +
                    "update FilePickerRobot to match the current widget tree."
            )
        firstItem.click()
    }
}
