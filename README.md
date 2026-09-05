# Tagalong: Video Cutter

*Trim and convert video. Your metadata comes along.*

---

When you trim a clip in a typical mobile editor, the output loses the date it was shot, the GPS coordinates, and the camera information the phone recorded. The video is fine; the record of when and where it happened is gone. The gallery then files it under today's date, and the original context is unrecoverable.

Tagalong fixes that one problem.

## What Tagalong doesn't do

- No multi-track timeline
- No filters, transitions, stickers, or music
- No account, no cloud, no export watermark

## What gets preserved

| | |
|---|---|
| **Creation date** | The date the video was shot, not the date it was edited |
| **GPS location** | Coordinates embedded in the source, unchanged |
| **Camera information** | Make, model, and manufacturer-specific tags |
| **Orientation** | Portrait clips stay portrait, with rotation properly signalled rather than baked into the frames |
| **Gallery date** | The date your gallery displays — stored separately from container metadata, and the thing most editors silently get wrong |

## How It Works

Tagalong uses FFmpeg to copy the video and audio streams without re-encoding, with options that carry all container tags through to the output unchanged. Cuts snap to the nearest keyframe — an inherent constraint of lossless cutting that the app surfaces rather than hides.

Files are selected via `ACTION_OPEN_DOCUMENT` rather than the standard Android Photo Picker (which would otherwise strip GPS tags).

## Install

Download the latest APK from [GitHub Releases](https://github.com/harvzor/tagalong/releases) and install it on your phone.

## Permissions

| Permission | Why |
|---|---|
| **ACCESS_MEDIA_LOCATION** | Android's media framework strips GPS location tags from any `openInputStream` call made without this permission — even when `ACTION_OPEN_DOCUMENT` is used. This permission ensures Tagalong receives an unredacted byte stream for the file you explicitly selected. It is used exclusively to read location that is already embedded in that file; the app has no analytics, no network calls, and no backend. |

## Building

The only host dependency is Docker (BuildKit-capable). No Android SDK, JDK, or Gradle installation required.

```bash
docker build --output=out .
```

The APK is written to `./out/app-debug.apk`.

To build a signed release APK locally (requires a keystore — see [Releases](#releases)):

```bash
RELEASE_STORE_PASSWORD=<store-password> \
RELEASE_KEY_ALIAS=release \
RELEASE_KEY_PASSWORD=<key-password> \
docker build \
  --build-arg BUILD_TYPE=release \
  --build-arg VERSION=1.0.0 \
  --secret id=keystore,src=./release.keystore \
  --secret id=store_password,env=RELEASE_STORE_PASSWORD \
  --secret id=key_alias,env=RELEASE_KEY_ALIAS \
  --secret id=key_password,env=RELEASE_KEY_PASSWORD \
  --output=out \
  .
```

The signed APK is written to `./out/tagalong-<version>.apk`. The keystore and credential values are never baked into any image layer.

### Local emulator testing

For local testing, start an Android emulator and run this from the repository root:

```bash
./scripts/push-fixtures-to-emulator.sh
```

The script pushes supported videos from `fixtures/` into the emulator's `/sdcard/DCIM/` directory and triggers Android's media scanner. The videos will then be available in Gallery and in Tagalong's **Pick video** picker. The emulator must be running and `adb` must be available from the Android SDK platform-tools. Rerun the command after wiping or replacing an emulator.

### Instrumented tests

Instrumented tests require a connected Android emulator. The current test setup has been verified against the `Medium_Phone` AVD running Android 17 (API 37), with the `sdk_gphone16k_arm64` device image.

Confirm that the emulator is connected:

```bash
adb devices
adb -s emulator-5554 emu avd name
```

Run the app end-to-end test:

```bash
# macOS/Linux
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.tagalong.app.E2eCutTest

# Windows PowerShell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=dev.tagalong.app.E2eCutTest"
```

Run the engine instrumented test suite:

```bash
# macOS/Linux
bash ./gradlew :engine:connectedAndroidTest

# Windows PowerShell
.\gradlew.bat :engine:connectedAndroidTest
```

## Releases

Pushing a version tag triggers an automated GitHub Actions workflow that builds a signed release APK and attaches it to the corresponding GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Signing

Release APKs are signed inside the Docker build using a keystore stored as GitHub Actions secrets. The keystore is mounted as a BuildKit secret (never baked into any image layer). Four repository secrets must be configured:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore file |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore store password |
| `RELEASE_KEY_ALIAS` | Key alias within the keystore |
| `RELEASE_KEY_PASSWORD` | Key password |

To populate these secrets:

```bash
gh secret set RELEASE_KEYSTORE_BASE64 --body "$(base64 -w0 release.keystore)"
gh secret set RELEASE_KEYSTORE_PASSWORD --body "<store-password>"
gh secret set RELEASE_KEY_ALIAS --body "release"
gh secret set RELEASE_KEY_PASSWORD --body "<key-password>"
```

> **Keep the keystore safe.** If it is lost, APKs signed with a new keystore will be treated by Android as a different app — existing users will need to uninstall and reinstall.
