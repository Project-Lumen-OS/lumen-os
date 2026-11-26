#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Customize Home host tests (plain JDK, no android.jar): the stored layout and its repair, folders, the looks as data
# (assets/home/looks.tsv and a name for each in every language), the dock with folders, where the parts go for each
# layout, free up memory, the network mark, the settings backup file, and the wiring greps.
# sh apps/LumenHome/test/layout/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/homelayout.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/home"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S"/model/*.java "$S/data/Card.java" "$S/data/HomeData.java" \
  "$HERE/../core/lumen-ui/src/org/lumen/ui/Grid.java" "$HERE/test/layout/LayoutTest.java"
"$JAVA_HOME/bin/java" -Dhome.dir="$HERE" -cp "$OUT" org.lumen.home.model.LayoutTest
