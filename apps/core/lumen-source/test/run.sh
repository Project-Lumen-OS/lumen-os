#!/bin/bash
# lumen-source host tests: bash apps/core/lumen-source/test/run.sh
#  1. SourceTest: the stock cycle per model (devices/*/source.tsv), quick presses, the card's text, bad data;
#  2. check_source.py: recipes, key receivers on the first DOWN, the X50 key route, the card's one-time slide, texts
#     reused from the old source list, nothing of the old right side list left.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
GSI=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || dirname "$(dirname "$(readlink -f "$(which javac)")")")}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_source.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/source"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$S/SourceRule.java" "$S/SourceCycle.java" "$S/SourceStep.java" "$S/SourcePress.java" "$S/OsdText.java" \
  "$HERE/test/SourceTest.java"
bad=0
"$JAVA_HOME/bin/java" -Dgsi="$GSI" -cp "$OUT" org.lumen.source.SourceTest || bad=1
python3 -B "$HERE/test/check_source.py" "$GSI" || bad=1
[ "$bad" = 0 ] && echo "ok   lumen-source: all green" || echo "FAIL lumen-source"
exit $bad
