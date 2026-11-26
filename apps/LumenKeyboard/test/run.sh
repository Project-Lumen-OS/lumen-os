#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# run.sh: host tests of Lumen Keyboard (no device). Pure rules and the D-pad on every page (KeyboardTest), every
# letter of every language (alphabet_check.py), then pages, subtypes, texts and wiring (check_res.py).
# Optional: APK=1 also builds the APK with apps/build_apk.sh (needs the Android SDK).
#   sh apps/LumenKeyboard/test/run.sh
set -eu
T=$(cd "$(dirname "$0")" && pwd)
APP=$(dirname "$T")
JAVA_HOME=${JAVA_HOME:-$( [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 2>/dev/null || echo /usr/lib/jvm/java-21-openjdk-amd64)}
B=${BUILD_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/lumenkb.XXXXXX")}
mkdir -p "$B/classes"
"$JAVA_HOME/bin/javac" -nowarn -d "$B/classes" \
  "$APP/src/org/lumen/keyboard/KeyboardLanguage.java" \
  "$APP/src/org/lumen/keyboard/LanguagePolicy.java" \
  "$APP/src/org/lumen/keyboard/KeyGrid.java" \
  "$T/org/lumen/keyboard/PageModel.java" \
  "$T/org/lumen/keyboard/KeyboardTest.java"
"$JAVA_HOME/bin/java" -cp "$B/classes" org.lumen.keyboard.KeyboardTest "$APP"
python3 -B "$T/alphabet_check.py"
python3 -B "$T/check_res.py"
if [ "${APK:-0}" = 1 ]; then
  BUILD_DIR="$B/apk" sh "$APP/../build_apk.sh" "$APP" "$B/LumenKeyboard.apk" >/dev/null
  echo "run.sh: APK builds"
fi
[ -n "${BUILD_DIR:-}" ] || rm -rf "$B"
echo "run.sh: ALL PASSED"
