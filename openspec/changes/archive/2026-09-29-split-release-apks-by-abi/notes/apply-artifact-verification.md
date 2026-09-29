# Group 5 — published artifact set verified

All figures measured, not projected. Version used for install testing: `0.8.0-rc1`,
signed with a throwaway local keystore (`_local-test-keystore.jks`, 30-day validity,
CN=local-install-test) — **not** the release key, deleted after use.

## After figures — 1.1 before was 243 MB single combined APK

| Artifact | Size | ABIs present (`lib/`) | 32-bit present |
|---|---|---|---|
| `arm64-v8a` | 70 MB | arm64-v8a | no |
| `x86_64` | 76 MB | x86_64 | no |
| `universal` | 118 MB | arm64-v8a, x86_64 | no |

A user on a modern phone downloads **70 MB instead of 243 MB** — the change's stated goal.
Total uploaded per release rises to ~264 MB across three assets (accepted in the design).

### 5.1 — exactly three release APKs
`find apk/release -name '*.apk'` → 3. Names: `app-{arm64-v8a,x86_64,universal}-release[-unsigned].apk`.
Signature verified with `apksigner verify`: v2 scheme, 1 signer.

### 5.2 — per-artifact native library listing
`arm64-v8a` → only `lib/arm64-v8a/`. `x86_64` → only `lib/x86_64/`. 11 `.so` each.

### 5.3 — universal not broader than the shipped set
Set union of the two separate artifacts == the universal's ABI set. **MATCH.**
This FAILED before task 3.6 and is the reason 3.6 exists: with `splits.abi.include` alone,
the universal merged all four ABIs from the ffmpeg-kit AAR (243 MB, 52 `.so`, 32-bit intact).

### 5.4 — no artifact contains 32-bit
`armeabi-v7a` and `x86` `.so` count per artifact: **0 / 0 / 0**. Removal is real and complete,
not merely an absent split.

### 5.5 — preservation contract on the installed `arm64-v8a` artifact
Installed on the arm64-v8a emulator (`primaryCpuAbi=arm64-v8a`), cut
`google-pixel-10a.mp4` end-to-end through the real picker UI. App reported
"✓ All 15 tags preserved"; verified independently with `ffprobe` on the pulled output:

```
creation_time = 2026-09-05T16:47:24.000000Z   (source → output, unchanged)
location      = +52.5562+13.3418/
location-eng  = +52.5562+13.3418/
com.android.model = Pixel 10a
```

Both the generic `location` and the QuickTime `©xyz` representation survive. Output
`/sdcard/Movies/Tagalong/google-pixel-10a_from_00-00-00-000_to_00-00-06-548.mp4`.

Note: `ACCESS_MEDIA_LOCATION` needs **both** `pm grant` **and**
`appops set <pkg> ACCESS_MEDIA_LOCATION allow` — the app-op defaults to *ignore*.

### 5.6 — identical result from the `universal` artifact
Uninstalled, clean state, installed the universal artifact (installs fine on arm64:
`primaryCpuAbi=arm64-v8a`, satisfying "universal installs on every supported architecture").
Repeating the identical cut produced a separate output file. Full sorted `format_tags`
diff of the two outputs: **no difference** — identical surviving tag sets from two
different installed artifacts.

> Careful step worth recording: the universal cut wrote `..._to_00-00-06-548 (1).mp4`
> rather than overwriting 5.5's file. Pulling by the 5.5 filename would silently have
> compared 5.5 against itself. Confirmed distinct files by `date_added` + mtime
> (22:29 vs 22:36) before asserting.

### 5.7 — architecture mismatch refused at install time
Device `ro.product.cpu.abilist=arm64-v8a`; installing the `x86_64` artifact:

```
INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries, res=-113
```

**Demonstrated on a real mismatched target**, not reasoned. Exit status 1, nothing installed —
incompatibility is surfaced at install time rather than installing and failing later, which is
exactly the spec scenario "Unsupported device cannot install".

## Local-testing caveat

These three artifacts were signed with a throwaway key, so they prove **installability, ABI
selection and the preservation contract**. Release signing itself is exercised by CI with the
repository keystore (group 7). The three artifacts must not be treated as publishable.
