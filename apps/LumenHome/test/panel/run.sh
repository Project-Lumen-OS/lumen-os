#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Customize Home side panel host tests (plain JDK, no android.jar): whose OK press it is, where the focus goes when a
# page is filled again, and the wiring greps of the 2026-11-23 fix. The key walk on an emulator is test/ui/run.sh.
# sh apps/LumenHome/test/panel/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/homepanel.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/home"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S/model/KeyPress.java" "$S/model/PanelFocus.java" \
  "$HERE/test/panel/PanelTest.java"
"$JAVA_HOME/bin/java" -Dhome.dir="$HERE" -cp "$OUT" org.lumen.home.model.PanelTest
