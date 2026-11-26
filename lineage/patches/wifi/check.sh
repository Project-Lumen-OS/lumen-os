#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The Wi-Fi patches apply to the pinned packages/modules/Wifi revision (read-only fetch of the touched files).
# sh lineage/patches/wifi/check.sh ; exit 0 = every patch applies cleanly
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
REV=$(sed -n 's#.*name="LineageOS/android_packages_modules_Wifi".*revision="\([0-9a-f]*\)".*#\1#p' "$HERE/../../manifests/lumen-lineage-21.xml")
[ -n "$REV" ] || { echo "FAIL no pinned Wifi revision in the manifest"; exit 1; }
W=$(mktemp -d "${TMPDIR:-/tmp}/wifipatch.XXXXXX")
trap 'rm -rf "$W"' EXIT
for p in "$HERE"/*.patch; do
  for f in $(sed -n 's#^--- a/packages/modules/Wifi/\([^	]*\).*#\1#p' "$p"); do
    mkdir -p "$W/packages/modules/Wifi/$(dirname "$f")"
    curl -fsSL -o "$W/packages/modules/Wifi/$f" "https://raw.githubusercontent.com/LineageOS/android_packages_modules_Wifi/$REV/$f"
  done
  (cd "$W" && patch -p1 --dry-run -s < "$p") || { echo "FAIL $(basename "$p") does not apply to $REV"; exit 1; }
  echo "ok   $(basename "$p") applies to $REV"
done
