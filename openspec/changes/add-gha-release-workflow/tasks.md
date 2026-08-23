## 1. Pre-flight: populate secrets

- [x] 1.1 Base64-encode the existing `release.keystore` from `android-bluetooth-bouncer` and set `RELEASE_KEYSTORE_BASE64` on `harvzor/tagalong` via `gh secret set RELEASE_KEYSTORE_BASE64 -R harvzor/tagalong`
- [x] 1.2 Set `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`release`), and `RELEASE_KEY_PASSWORD` on `harvzor/tagalong` with the same values used in `android-bluetooth-bouncer`

## 2. Add the workflow file

- [x] 2.1 Create `.github/workflows/release-build.yml` triggering on `v*` tags, decoding the keystore, running `docker build` with signing secrets and `--output type=local,dest=out`, and uploading `out/*.apk` via `softprops/action-gh-release@v2`

## 3. Verify

- [ ] 3.1 Push a test tag (e.g. `v0.1.0`) and confirm the workflow runs to completion in GitHub Actions
- [ ] 3.2 Confirm the GitHub Release is created for the tag with `tagalong-0.1.0.apk` attached
- [ ] 3.3 Verify the APK is signed: `apksigner verify --verbose tagalong-0.1.0.apk` (or `apksigner.bat` on Windows)
