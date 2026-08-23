# Tagalong

An Android app for **lossless video trimming with complete metadata preservation**. Every container tag in the source file — GPS location, `creation_time`, device make/model, brand-specific tags — survives the cut unchanged.

## Why ACTION_OPEN_DOCUMENT instead of the Photo Picker

Tagalong's core contract is that no metadata is lost during a trim. The standard Android Photo Picker (`PickVisualMedia` / `ACTION_PICK`) makes this impossible:

| What the Photo Picker breaks | Why it cannot be worked around |
|---|---|
| **GPS location tags** — the Google Photo Picker module strips `location` and `location-eng` tags from the `openInputStream` byte stream, regardless of `ACCESS_MEDIA_LOCATION` being declared | No public API exists to request an unredacted byte stream through the Photo Picker path; `MediaStore.setRequireOriginal` throws `UnsupportedOperationException` on the Play Store module |
| **Real filename** — `DISPLAY_NAME` is replaced with the picker's internal numeric ID (e.g. `1000000072`) | The output file would inherit a meaningless name the user did not give it |
| **Gallery path** — `RELATIVE_PATH` is nulled out | The path label shown to the user while trimming would be incomplete |

`ACTION_OPEN_DOCUMENT` is the standard Android mechanism for granting an app direct, persistent, unredacted access to a single file the user explicitly selects. The app requests no broad media permissions and accesses only the file the user picks — the narrowest permission model that satisfies the metadata-preservation contract.

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
# Encode the keystore
gh secret set RELEASE_KEYSTORE_BASE64 --body "$(base64 -w0 release.keystore)"
gh secret set RELEASE_KEYSTORE_PASSWORD --body "<store-password>"
gh secret set RELEASE_KEY_ALIAS --body "release"
gh secret set RELEASE_KEY_PASSWORD --body "<key-password>"
```

> **Keep the keystore safe.** If it is lost, APKs signed with a new keystore will be treated by Android as a different app — existing users will need to uninstall and reinstall.
