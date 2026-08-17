package dev.tagalong.app

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * UIAutomator interactions for the system photo picker.
 *
 * On the Pixel_7_API_34 emulator the picker runs as
 * `com.google.android.providers.media.module` and uses `android.widget.GridView`
 * (resource id `picker_tab_recyclerview`) — not a RecyclerView.
 *
 * Derived from: `adb shell uiautomator dump` taken while the picker was open.
 *
 * Known fragility: the picker's widget tree can shift between OS versions. Keeping this
 * class isolated means updates are contained (design D4/non-goals).
 */
object PhotoPickerRobot {

    // Package name confirmed by UI dump on Pixel_7_API_34 (API 34, swiftshader emulator).
    // AOSP builds use "com.android.providers.media.module"; adjust if you target a different image.
    private const val PICKER_PKG = "com.google.android.providers.media.module"
    private const val GRID_RES = "$PICKER_PKG:id/picker_tab_recyclerview"

    private const val WAIT_PICKER_MS = 10_000L
    private const val WAIT_CONFIRM_MS = 2_000L

    /**
     * Waits for the picker to appear, selects the first item in the media grid, then
     * confirms if an "Add"/"Done" button appears (some picker builds select on tap and
     * return immediately without a confirm step).
     *
     * Call from a test after an action that opens the system photo picker.
     */
    fun selectFirstItem(device: UiDevice) {
        // 1. Wait for picker process to become foreground
        device.wait(Until.hasObject(By.pkg(PICKER_PKG)), WAIT_PICKER_MS)
            ?: error("Photo picker ($PICKER_PKG) did not appear within ${WAIT_PICKER_MS}ms")

        // 2. Find the media grid by resource id (GridView on API 34 emulator)
        val grid = device.wait(
            Until.findObject(By.res(GRID_RES)),
            WAIT_PICKER_MS,
        ) ?: error(
            "Picker grid ($GRID_RES) not found. " +
                "Run `adb shell uiautomator dump` while the picker is open and update " +
                "GRID_RES / PICKER_PKG in PhotoPickerRobot (task 5.4)."
        )

        // 3. Click the first clickable FrameLayout item (thumbnail cells)
        val firstItem = grid.findObjects(
            By.clazz("android.widget.FrameLayout").clickable(true)
        ).firstOrNull()
            ?: error("No clickable FrameLayout items found in picker grid")
        firstItem.click()

        // 4. On this build, tapping a video cell picks it immediately (no confirm button
        //    appears). Wait briefly — if "Add" or "Done" does appear, click it.
        val confirmButton =
            device.wait(Until.findObject(By.text("Add").pkg(PICKER_PKG)), WAIT_CONFIRM_MS)
                ?: device.findObject(By.text("Done").pkg(PICKER_PKG))
        confirmButton?.click() // null is fine — picker already returned the result
    }
}
