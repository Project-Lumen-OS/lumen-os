#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-sensors host tests: bash apps/core/lumen-sensors/test/run.sh
#  1. SensorTest: items, the HAL budget (2 calls a second, none while hidden, the distance once a minute), rows, parser;
#  2. check_sensors.py: per-model tables, recipes, readers without forbidden calls, texts, the old app gone.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_sensors.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/sensors"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$S/SensorItem.java" "$S/SensorValues.java" "$S/SensorItems.java" "$S/SensorBudget.java" "$S/SensorSheet.java" \
  "$S/SystemStats.java" "$HERE/test/SensorTest.java"
bad=0
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.sensors.SensorTest || bad=1
python3 -B "$HERE/test/check_sensors.py" || bad=1
[ "$bad" = 0 ] && echo "ok   lumen-sensors: all green" || echo "FAIL lumen-sensors"
exit $bad
