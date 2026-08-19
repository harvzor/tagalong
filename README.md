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

To build a signed release APK locally (requires a keystore):

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
