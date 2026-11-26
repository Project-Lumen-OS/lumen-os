#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Tile labels in every language with the real Inter font (LabelFit): sh apps/LumenHome/test/label/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
G=$(cd "$HERE/../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/homelabel.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$HERE/src/org/lumen/home/ui/LabelFit.java" \
  "$HERE/test/label/LabelFitTest.java"
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -Dgsi="$G" -cp "$OUT" org.lumen.home.ui.LabelFitTest
