#!/usr/bin/env bash
#
# Collect the release/debug APKs this build produced into an export directory,
# naming each per-artifact so that an architecture-split build (splits.abi +
# universalApk) publishes every output instead of collapsing them onto one name.
#
# Used by the Dockerfile export step. Kept as a standalone script so the exact
# selection and naming logic is one source of truth — and so it can be exercised
# directly against a host build tree, not only inside a full container build.
#
# Usage:
#   collect-apks.sh <variant> <version> <out-dir> [apk-root]
#
#   <variant>   build variant to export, e.g. "release" or "debug". Only outputs
#               under a directory of this exact name are considered; anything
#               under an "androidTest" tree is never exported.
#   <version>   version string for filenames. If empty, each APK keeps its
#               build-default filename.
#   <out-dir>   directory to write the collected APK(s) into.
#   [apk-root]  root of AGP's APK output tree. Default: app/build/outputs/apk.
#
# Exit status is non-zero if two outputs would land on the same destination name.

set -euo pipefail

variant="${1:-}"
version="${2:-}"
out_dir="${3:-}"
apk_root="${4:-app/build/outputs/apk}"

if [[ -z "$variant" || -z "$out_dir" ]]; then
    echo "Error: variant and out-dir are required." >&2
    echo "Usage: $0 <variant> <version> <out-dir> [apk-root]" >&2
    exit 2
fi

if [[ ! -d "$apk_root" ]]; then
    echo "Error: APK output root does not exist: $apk_root" >&2
    exit 1
fi

# AGP writes architecture-split outputs either flat (<root>/<variant>/*.apk) or,
# on other AGP versions, nested (<root>/<abi>/<variant>/*.apk). A search for a
# leaf directory named exactly <variant> catches both layouts. The androidTest
# guard is essential, not theoretical: instrumented-test APKs live at
# <root>/androidTest/<variant>/*-androidTest.apk, whose leaf directory matches
# <variant> too, so path-scoping alone is not enough to exclude them.
collect_paths() {
    find "$apk_root" \
        -path "*/androidTest" -prune -o \
        -path "*/${variant}" -type d -print \
    | while IFS= read -r dir; do
        find "$dir" -maxdepth 1 -type f -name "*.apk" -print
    done
}

# Map a derived architecture identifier to its canonical release-name spelling.
#
# Explicit and closed on purpose. A blanket underscore-to-hyphen rewrite is
# WRONG here: it keeps "arm64_v8a" -> "arm64-v8a" correct but mangles "x86_64"
# into "x86-64", which is not a real architecture name. Anything unrecognised is
# passed through untouched rather than guessed at.
canon_id() {
    case "$1" in
        arm64_v8a)        printf 'arm64-v8a' ;;
        x86_64)           printf 'x86_64' ;;
        x86)              printf 'x86' ;;
        armeabi-v7a|armeabi_v7a) printf 'armeabi-v7a' ;;
        universal)        printf 'universal' ;;
        "")               printf 'universal' ;;   # an unsplit build targets every bundled ABI
        *)                printf '%s' "$1" ;;
    esac
}

mkdir -p "$out_dir"

# Ledger of destination paths already written, one per line. A plain string (not
# an associative array) so the script runs on the macOS default bash 3.2 as well
# as the container's modern bash.
seen_dest=""
count=0

while IFS= read -r apk_path; do
    [[ -z "$apk_path" ]] && continue

    filename="$(basename "$apk_path")"

    if [[ -n "$version" ]]; then
        # Derive the architecture identifier from this one output, not from a
        # fixed list. Strip ".apk", an optional "-unsigned" suffix, then the
        # trailing "-<variant>", then the leading "<module>-". Whatever is left
        # is the raw abi token, or empty for a SINGLE unsplit build.
        base="${filename%.apk}"
        base="${base%-unsigned}"
        base="${base%-${variant}}"
        case "$base" in
            *-*) base="${base#*-}" ;;   # drop "<module>-"
            *)   base="" ;;             # bare "<module>": no abi marker present
        esac

        id="$(canon_id "$base")"
        dest="$out_dir/tagalong-${version}-${id}.apk"
    else
        # No version requested: preserve each APK's build-default filename.
        dest="$out_dir/$filename"
    fi

    case "\n${seen_dest}\n" in
        *"\n${dest}\n"*)
            echo "Error: two outputs collide on destination '$dest' ('$apk_path')." >&2
            echo "Refusing to overwrite; release artifact naming must be one-to-one." >&2
            exit 1
            ;;
    esac
    seen_dest="${seen_dest}${dest}\n"

    cp "$apk_path" "$dest"
    printf 'collected %s -> %s\n' "$apk_path" "$dest"
    count=$((count + 1))
done < <(collect_paths | sort)

if [[ "$count" -eq 0 ]]; then
    echo "Error: no ${variant} APKs found under $apk_root (excluding androidTest)." >&2
    exit 1
fi

printf 'done: %d %s APK(s) exported\n' "$count" "$variant"
