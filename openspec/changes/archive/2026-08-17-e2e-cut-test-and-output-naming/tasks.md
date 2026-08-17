## 1. Output naming — model

- [x] 1.1 Add `originalDisplayName: String` field to `PickedSource` in `CutUiState.kt`
- [x] 1.2 Add `private fun formatTimestamp(ms: Long): String` to `CutViewModel` that produces `HH-MM-SS-mmm` using `%02d-%02d-%02d-%03d`
- [x] 1.3 In `CutViewModel.onVideoPicked`, query `MediaStore.MediaColumns.DISPLAY_NAME` via `ContentResolver` on the picked Uri and pass the result (fallback `"video"`) into `PickedSource`
- [x] 1.4 In `CutViewModel.runCut`, replace the `"tagalong-${System.currentTimeMillis()}.mp4"` display name with `"${baseNameOf(source.originalDisplayName)}_from_${formatTimestamp(state.startMs)}_to_${formatTimestamp(state.endMs)}.mp4"` — where `baseNameOf` strips the file extension

## 2. Test infrastructure — build and assets

- [x] 2.1 Add `androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")` to `app/build.gradle.kts`
- [x] 2.2 Add `androidTestImplementation(platform(libs.androidx.compose.bom))`, `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`, and `debugImplementation("androidx.compose.ui:ui-test-manifest")` to `app/build.gradle.kts`
- [x] 2.3 Add `androidTestImplementation("androidx.test.ext:junit:1.2.1")`, `androidTestImplementation("androidx.test:runner:1.6.2")`, and `androidTestImplementation("androidx.test:rules:1.6.1")` to `app/build.gradle.kts`
- [x] 2.4 Copy `fixtures/xiaomi-poco-x5.mp4` (repo root) to `app/src/androidTest/assets/xiaomi-poco-x5.mp4`

## 3. Test utilities

- [x] 3.1 Create `app/src/androidTest/java/dev/tagalong/app/MediaStoreSeeder.kt` with:
  - `fun insert(context: Context, assetName: String): Uri` — writes the asset into `MediaStore.Video.Media` on `VOLUME_EXTERNAL_PRIMARY` and returns the inserted content Uri
  - `fun delete(context: Context, vararg uris: Uri?)` — deletes the given Uris from MediaStore (used in `@After`)
  - `fun findByDisplayName(context: Context, displayName: String): Uri?` — queries `MediaStore.Video.Media` for a file whose `DISPLAY_NAME` matches and returns its Uri (or null)
- [x] 3.2 Create `app/src/androidTest/java/dev/tagalong/app/PhotoPickerRobot.kt` with:
  - `fun selectFirstItem(device: UiDevice)` — waits up to 10 s for the picker package (`com.android.providers.media.module`) to appear, finds the first `clickable(true)` child of the `RecyclerView` in that package, clicks it, then clicks `By.text("Add")` (falling back to `By.text("Done")`) to confirm

## 4. E2E test

- [x] 4.1 Create `app/src/androidTest/java/dev/tagalong/app/E2eCutTest.kt` annotated `@RunWith(AndroidJUnit4::class)` with:
  - `@get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()`
  - `@Before`: call `MediaStoreSeeder.insert` with the fixture asset; store the returned Uri
  - `@After`: call `MediaStoreSeeder.delete` on the seeded Uri and (if found) the output Uri
- [x] 4.2 Implement `fun fullFlow_preservesMetadata()` in `E2eCutTest`:
  1. Probe the fixture in `cacheDir` using `MetadataReader.probe` for later comparison (copy asset to cache first)
  2. `composeTestRule.onNodeWithText("Pick video").performClick()`
  3. `PhotoPickerRobot.selectFirstItem(UiDevice.getInstance(...))`
  4. `composeTestRule.waitUntil(30_000)` until a node with text `"Cut and save"` exists
  5. `composeTestRule.onNodeWithText("Cut and save").performClick()`
  6. `composeTestRule.waitUntil(60_000)` until a node with text `"Saved"` (substring) exists
  7. Compute `expectedDisplayName` from the fixture's base name and the default full-duration range
  8. `MediaStoreSeeder.findByDisplayName(context, expectedDisplayName)` — assert non-null
  9. Open the output via `ContentResolver.openFileDescriptor` and probe with `MetadataReader`
  10. Assert `MetadataAssertions.sourceTagsSubsetOfOutput(sourceProbe.formatTags, outputProbe.formatTags).isSubset`
  11. Assert `creation_time` and `location` tags match the source

## 5. Verify

- [x] 5.1 Boot the emulator if not already running: `& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_7_API_34 -no-snapshot -gpu swiftshader_indirect`
- [x] 5.2 Confirm device is visible: `adb devices`
- [x] 5.3 Run `./gradlew.bat :app:connectedAndroidTest` and confirm `E2eCutTest` passes
- [x] 5.4 If `PhotoPickerRobot` fails to find "Add"/"Done", run `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` while the picker is open and update the button selector to match the actual label
