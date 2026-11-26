#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the shared QR code (apps/core/lumen-qr): sh apps/core/lumen-qr/test/run.sh
#  1. QrCodeTest: versions, the length limit, the matrices of 58 texts pinned (the first-run app's former encoder drew
#     the same), whole-pixel module geometry of QrView;
#  2. the module sources compile against Android 34 (android.jar + the framework headers);
#  3. no other QR encoder in the shared modules, the projector apps or the first-run app.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_qr.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/pure" "$OUT/android"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/pure" "$HERE/src/org/lumen/qr/QrCode.java" \
  "$HERE/test/org/lumen/qr/QrCodeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/pure" org.lumen.qr.QrCodeTest
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -d "$OUT/android" -cp "$HJ:$AJ" \
  "$HERE"/src/org/lumen/qr/*.java
echo "ok   android: the module compiles against android-34"
other=$(grep -rlE 'ECC_PER_BLOCK|class Qr(Code)? ' --include=*.java "$G/apps/core" "$G/apps/LumenProjector/core/src" \
  "$G/apps/LumenProjector/host/activity/src" "$G/apps/LumenProjector/vendor/xgimi/mt9681-aidl/src" "$G/apps/LumenProjector/vendor/xgimi/play6-hidl/src" "$G/apps/LumenSetup/src" \
  | grep -v "^$HERE/src/" || true)
if [ -n "$other" ]; then
  echo "FAIL another QR encoder: $other"
  exit 1
fi
echo "ok   one QR encoder in the shared modules, the projector apps and the first-run app"
echo "lumen-qr tests: OK"
