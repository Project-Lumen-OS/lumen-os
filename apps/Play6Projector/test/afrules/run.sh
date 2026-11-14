#!/bin/sh
# Play 6 focus rules host checks (P6AfRules is plain Java): sh test/afrules/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/P6AfRules.java" "$HERE/test/afrules/P6AfRulesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.P6AfRulesTest
# MotorController (Android, not compiled here) starts every move through P6AfRules.startMove with the motor command
# first, and writes manual_moving=true nowhere else
M="$HERE/src/org/lumen/projector/MotorController.java"
grep -qF 'P6AfRules.startMove(() -> sendMove(d), () -> setFlag(true), this::sendStop)' "$M" \
  || { echo "FAIL MotorController does not start its moves through P6AfRules.startMove (motor, flag, stop)"; exit 1; }
[ "$(grep -cF 'setFlag(true)' "$M")" = 1 ] \
  || { echo "FAIL MotorController writes manual_moving=true outside P6AfRules.startMove"; exit 1; }
[ "$(grep -cF 'client.manualFocus(d == 1 ? ManualFocus.STOCK_LEFT : ManualFocus.STOCK_RIGHT)' "$M")" = 1 ] \
  || { echo "FAIL MotorController sends the move command outside sendMove"; exit 1; }
echo "OK MotorController starts moves through P6AfRules.startMove"
