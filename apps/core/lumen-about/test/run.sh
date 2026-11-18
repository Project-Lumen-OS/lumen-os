#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Host checks of the XGIMI side in Settings > About (apps/core/lumen-about): sh apps/core/lumen-about/test/run.sh
#  1. AboutTest: the facts (model code, firmware, stick name), the About rows, the catalog (signature, frame, official
#     addresses, version mapping, the published catalog with the real list certificate), the screen's choice;
#  2. every module source compiles against Android 34 (android.jar + the framework headers, a stub R of the module
#     texts, the lumen-qr sources);
#  3. check_repo.py: texts in every language, the wiring of every projector app, the TvSettings fix's side of the
#     contract, the list certificate, read only;
#  4. no install jargon in any text of any app (tools/i18n/ui_words.py).
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
JSON=$G/apps/core/lumen-updater/test/parity/json
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_about.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S=$HERE/src/org/lumen/about
PURE="XgimiFacts LightEngine FirmwareParts OfficialFile RestoreCatalog AboutRow AboutRows RowText Guide WayBack"
set --
for c in $PURE; do set -- "$@" "$S/$c.java"; done
mkdir -p "$OUT/pure" "$OUT/android/org/lumen/projector"
# a javac argument file, one quoted path per line: the checkout path may hold blanks ("XGIMI PLAY 6")
find "$JSON" -name '*.java' | sed 's/.*/"&"/' > "$OUT/json.files"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/pure" @"$OUT/json.files" "$@" \
  "$HERE/test/org/lumen/about/AboutTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/pure" org.lumen.about.AboutTest "$HERE"
{
  echo "package org.lumen.projector;"
  echo "public final class R {"
  echo "  public static final class string {"
  sed -n 's/.*<string name="\([a-z0-9_]*\)">.*/    public static final int \1 = 1;/p' "$HERE/res/values/lumen_strings_about.xml"
  echo "  }"
  echo "}"
} > "$OUT/android/org/lumen/projector/R.java"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -d "$OUT/android" -cp "$HJ:$AJ" \
  "$OUT/android/org/lumen/projector/R.java" "$S"/*.java "$G"/apps/core/lumen-qr/src/org/lumen/qr/*.java
echo "ok   android: $(ls "$S"/*.java | wc -l | tr -d ' ') module sources compile against android-34"
python3 -B "$HERE/test/check_repo.py" "$G"
python3 -B "$G/tools/i18n/ui_words.py" "$G"
echo "lumen-about tests: OK"
