#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the contribution client (apps/core/lumen-contrib): sh apps/core/lumen-contrib/test/run.sh
#  1. ContribTest: the signed body byte for byte, the signature, every answer of the base, failures as "no card";
#  2. KeystoreSigner compiles against Android 34 when android.jar is there (ANDROID_SDK or the usual SDK paths);
#  3. no "flash" anywhere in the module: in Lumen Installer it means writing firmware, the reward is "Spark" (RU
#     "Вспышка") and its base is spark.lumen-os.net (2026-11-21).
# VECTOR=FILE also writes one signed body for a check on the server side.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$( (/usr/libexec/java_home -v 21 || /usr/libexec/java_home -v 17) 2>/dev/null || ls -d /usr/lib/jvm/java-21-openjdk-* 2>/dev/null | head -1)
fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_contrib.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/contrib
set --
for f in "$S"/*.java; do [ "$(basename "$f")" = KeystoreSigner.java ] || set -- "$@" "$f"; done
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/pure" "$@" "$HERE/test/org/lumen/contrib/ContribTest.java"
"$JAVA_HOME/bin/java" ${VECTOR:+-Dlumen.contrib.vector=$VECTOR} -cp "$OUT/pure" org.lumen.contrib.ContribTest
AJ=""
for c in "${ANDROID_SDK:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$HOME/android-sdk"; do
  [ -n "$c" ] && [ -f "$c/platforms/android-34/android.jar" ] && { AJ=$c/platforms/android-34/android.jar; break; }
done
if [ -n "$AJ" ]; then
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -cp "$AJ" -d "$OUT/android" "$S"/*.java
  echo "lumen-contrib: Android side compiles"
else
  echo "lumen-contrib: no android.jar, Android side not compiled"
fi
if grep -rni "flash" "$HERE/src" "$HERE/README.md"; then
  echo "lumen-contrib: the word flash above: the reward is Spark (RU Вспышка)" >&2
  exit 1
fi
echo "lumen-contrib tests: OK"
