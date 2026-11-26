#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host test of lineage/patches/wifi/0001 (the AIDL V3 P2P calls decided by the supplicant's interface hash):
#   sh lineage/patches/wifi/test/run.sh
# 1. the patch applies to the pinned packages/modules/Wifi revision (the original file is fetched read-only from
#    GitHub once and kept in $TMPDIR, sha256 pinned below), 2. SupplicantP2pV3Calls, compiled from the patch itself
#    with the JDK alone, passes SupplicantP2pV3CallsTest, 3. wiring.py checks the patched SupplicantP2pIfaceHalAidlImpl.
# WIFI_PATCH=FILE tests another copy of the patch (test/mutate.py).
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
PATCH=${WIFI_PATCH:-$HERE/../0001-p2p-supplicant-v3-snapshot.patch}
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)
fi
REV=$(sed -n 's#.*name="LineageOS/android_packages_modules_Wifi".*revision="\([0-9a-f]*\)".*#\1#p' "$HERE/../../../manifests/lumen-lineage-21.xml")
[ -n "$REV" ] || { echo "FAIL no pinned Wifi revision in the manifest"; exit 1; }
P=service/java/com/android/server/wifi/p2p
ORIG_SHA=09f1efd6f50393088b3c8ed0df35a29f94239cd515200c473e6c2d9223fa92de
CACHE=${TMPDIR:-/tmp}/lumen-wifi-$REV
if [ ! -f "$CACHE/SupplicantP2pIfaceHalAidlImpl.java" ]; then
  mkdir -p "$CACHE"
  curl -fsSL -o "$CACHE/SupplicantP2pIfaceHalAidlImpl.java.part" \
    "https://raw.githubusercontent.com/LineageOS/android_packages_modules_Wifi/$REV/$P/SupplicantP2pIfaceHalAidlImpl.java"
  mv "$CACHE/SupplicantP2pIfaceHalAidlImpl.java.part" "$CACHE/SupplicantP2pIfaceHalAidlImpl.java"
fi
sha() { python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"; }
[ "$(sha "$CACHE/SupplicantP2pIfaceHalAidlImpl.java")" = "$ORIG_SHA" ] \
  || { echo "FAIL the fetched SupplicantP2pIfaceHalAidlImpl.java of $REV is not the pinned one"; exit 1; }
OUT=$(mktemp -d "${TMPDIR:-/tmp}/wifipatch_test.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/src/packages/modules/Wifi/$P"
cp "$CACHE/SupplicantP2pIfaceHalAidlImpl.java" "$OUT/src/packages/modules/Wifi/$P/"
(cd "$OUT/src" && patch -p1 -s --no-backup-if-mismatch < "$PATCH") || { echo "FAIL the patch does not apply to $REV"; exit 1; }
echo "ok   patch applies to $REV"
C=$OUT/src/packages/modules/Wifi/$P/SupplicantP2pV3Calls.java
if grep -n '^import android\.\|^import com\.android' "$C"; then
  echo "FAIL SupplicantP2pV3Calls must stay plain Java (host-tested)"; exit 1
fi
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" "$C" "$HERE/SupplicantP2pV3CallsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/c" com.android.server.wifi.p2p.SupplicantP2pV3CallsTest
python3 "$HERE/wiring.py" "$OUT/src/packages/modules/Wifi/$P/SupplicantP2pIfaceHalAidlImpl.java"
echo "wifi patch tests OK"
