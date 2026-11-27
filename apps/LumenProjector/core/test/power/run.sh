#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# hb-t7 sleep decision host check (power/StrGate with stubs for the few Android classes it uses) against the image
# props devices/goat_elegant/image/hb_lumen_prop.txt, then the stock STR prepare (power/StrPrepare, StrPrepareTest):
# sh test/power/run.sh
set -eu
# in the repository this suite runs inside the HB app as its recipe assembles it (apps/LumenProjector/core.features)
if [ -f "$(dirname "$0")/../../../core.features" ]; then
  G=$(cd "$(dirname "$0")/../../../../.." && pwd); mkdir -p "$G/build"; A=$(mktemp -d "$G/build/hbt.XXXXXX")
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device goat_elegant --app HbProjector --out "$A" --quiet
  rc=0; LUMEN_GSI=$G sh "$A/test/$(basename "$(dirname "$0")")/run.sh" || rc=$?; rm -rf "$A"; exit $rc
fi
HERE=$(cd "$(dirname "$0")/../.." && pwd)
# the gsi/ tree: LUMEN_GSI when set (the test runs from the assembled tree of the recipe, outside apps/), else the
# folder two levels up
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
T=$HERE/test/power
# StrGate has no HB copy: run from apps/HbProjector (not the assembled tree), take the file the recipe takes
SRC=$HERE/src/org/lumen/projector/power/StrGate.java
[ -f "$SRC" ] || SRC=$GSI/apps/LumenProjector/core/src/org/lumen/projector/power/StrGate.java
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$SRC" \
  "$T/stubs/android/content/Context.java" "$T/stubs/android/content/BroadcastReceiver.java" \
  "$T/stubs/android/content/Intent.java" "$T/stubs/android/content/IntentFilter.java" \
  "$T/stubs/android/os/SystemProperties.java" "$T/stubs/android/os/SystemClock.java" \
  "$T/stubs/android/util/Log.java" "$T/StrGateTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.power.StrGateTest "$GSI/devices/goat_elegant/image/hb_lumen_prop.txt"
# str-gmpf-1127 (GitHub issue 5): the stock STR prepare of the gmpf families, the issue's replay and the wiring
SP=$HERE/src/org/lumen/projector/power/StrPrepare.java
[ -f "$SP" ] || SP=$GSI/apps/LumenProjector/core/src/org/lumen/projector/power/StrPrepare.java
mkdir -p "$OUT/sp"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/sp" "$SP" "$T/StrPrepareTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/sp" org.lumen.projector.power.StrPrepareTest "$GSI"
