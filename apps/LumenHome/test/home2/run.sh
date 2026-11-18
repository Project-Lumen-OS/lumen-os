#!/bin/sh
# Lumen Home host tests (plain JDK, no android.jar): rows and the calm screen (1, 2, 3 and many Continue items),
# Continue texts, one item per series, app order, the D-pad map, the greeting, the Витрина's layout, the art rule and
# the hero binding. sh apps/LumenHome/test/home2/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/home2.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/home"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S"/model/*.java "$S/data/Card.java" \
  "$HERE/../core/lumen-ui/src/org/lumen/ui/Grid.java" \
  "$HERE/test/home2/HomeModelTest.java"
"$JAVA_HOME/bin/java" -Dhome.src="$S" -cp "$OUT" org.lumen.home.model.HomeModelTest
