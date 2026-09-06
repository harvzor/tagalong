#!/usr/bin/env bash

set -euo pipefail

# Push repository sample videos into the emulator's public DCIM directory and
# ask Android to index them so they appear in Gallery and ACTION_OPEN_DOCUMENT.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURES_DIR="${FIXTURES_DIR:-$REPO_ROOT/sample-videos}"
REMOTE_DIR="${REMOTE_DIR:-/sdcard/DCIM}"

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi

    local candidate
    for candidate in \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
        "$HOME/Library/Android/sdk/platform-tools/adb" \
        "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb"; do
        if [[ -n "$candidate" && -x "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

ADB_BIN="$(find_adb || true)"
if [[ -z "$ADB_BIN" ]]; then
    echo "Error: adb was not found. Add Android SDK platform-tools to PATH or set ANDROID_HOME." >&2
    exit 1
fi

# Use ANDROID_SERIAL when supplied. Otherwise require exactly one connected device
# so a sample cannot accidentally be pushed to the wrong emulator or phone.
ADB_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB_ARGS=(-s "$ANDROID_SERIAL")
else
    connected_devices=()
    while IFS= read -r device; do
        [[ -n "$device" ]] && connected_devices+=("$device")
    done < <(
        "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
    )

    case "${#connected_devices[@]}" in
        0)
            echo "Error: no connected Android device or emulator found." >&2
            echo "Start the emulator, then rerun this script." >&2
            exit 1
            ;;
        1)
            ADB_ARGS=(-s "${connected_devices[0]}")
            ;;
        *)
            echo "Error: multiple Android devices are connected:" >&2
            printf '  %s\n' "${connected_devices[@]}" >&2
            echo "Set ANDROID_SERIAL to choose one, for example:" >&2
            echo "  ANDROID_SERIAL=${connected_devices[0]} $0" >&2
            exit 1
            ;;
    esac
fi

adb() {
    "$ADB_BIN" "${ADB_ARGS[@]}" "$@"
}

if [[ ! -d "$FIXTURES_DIR" ]]; then
    echo "Error: sample-video directory does not exist: $FIXTURES_DIR" >&2
    exit 1
fi

# Keep this list explicit so unrelated files in sample-videos/ are not copied.
sample_files=()
while IFS= read -r -d '' fixture; do
    sample_files+=("$fixture")
done < <(
    find "$FIXTURES_DIR" -maxdepth 1 -type f \
        \( -iname '*.mp4' -o -iname '*.mov' -o -iname '*.m4v' -o -iname '*.3gp' -o -iname '*.webm' -o -iname '*.mkv' \) \
        -print0
)

if [[ "${#sample_files[@]}" -eq 0 ]]; then
    echo "Error: no supported sample videos found in $FIXTURES_DIR" >&2
    exit 1
fi

adb shell mkdir -p "$REMOTE_DIR"

for sample in "${sample_files[@]}"; do
    filename="$(basename "$sample")"
    remote_path="$REMOTE_DIR/$filename"

    echo "Pushing $sample -> $remote_path"
    adb push "$sample" "$remote_path"

    echo "Indexing $filename"
    adb shell am broadcast \
        -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "file://$remote_path" >/dev/null
done

echo "Done. ${#sample_files[@]} sample video(s) are in $REMOTE_DIR."
