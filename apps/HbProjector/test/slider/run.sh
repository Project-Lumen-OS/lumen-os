#!/bin/sh
# Picture slider writes while a key is held (panel/SlideCoalescer: one write in flight, newest value wins, 100 ms apart,
# one page re-read after the slide) and its wiring into the HB picture pages. sh test/slider/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/slider
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/SlideCoalescer.java" "$T/SlideCoalescerTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.SlideCoalescerTest
python3 "$T/check_wiring.py"
echo "slider: all passed"
