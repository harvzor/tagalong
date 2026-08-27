## 1. Nav graph

- [x] 1.1 Add `home` and `about` as nav destinations in `MainActivity.kt`; set `home` as the start destination
- [x] 1.2 Update `result` back navigation to pop to `home` instead of `trim`

## 2. HomeScreen

- [x] 2.1 Create `HomeScreen.kt` with a centred "Pick video" button
- [x] 2.2 Move the `OpenDocument` launcher from `TrimScreen.kt` to `HomeScreen.kt`
- [x] 2.3 Move the `ACCESS_MEDIA_LOCATION` permission request from `TrimScreen.kt` to `HomeScreen.kt`
- [x] 2.4 On successful pick, call `viewModel.onVideoPicked(uri)` then navigate to `trim`
- [x] 2.5 Add an About icon/button in the HomeScreen top bar that navigates to `about`

## 3. AboutScreen

- [x] 3.1 Create `AboutScreen.kt` showing app name and version (read from PackageManager at runtime)
- [x] 3.2 Add ffmpeg-kit-full-gpl attribution: name, version (2.1.0), author (Anton Karpenko), license (GPL v3 / LGPL v3)
- [x] 3.3 Make the ffmpeg-kit source link tappable (`https://github.com/sk3llo/ffmpeg-kit-flutter`) — opens browser
- [x] 3.4 Add app license line: "Tagalong is licensed under GPL v3"

## 4. TrimScreen cleanup

- [x] 4.1 Remove the empty-state composable (centred "Pick video" button) from `TrimScreen.kt`
- [x] 4.2 Remove the `OpenDocument` launcher and `ACCESS_MEDIA_LOCATION` permission request from `TrimScreen.kt`
- [x] 4.3 Add a `LaunchedEffect` guard: if `source` is null, pop back to `home` (handles process-death restore)

## 5. Verify

- [x] 5.1 Cold launch shows HomeScreen with "Pick video" button
- [x] 5.2 Picking a video navigates to TrimScreen; back returns to HomeScreen
- [x] 5.3 About screen shows ffmpeg-kit attribution and tappable link
- [x] 5.4 ResultScreen back returns to HomeScreen, not TrimScreen
