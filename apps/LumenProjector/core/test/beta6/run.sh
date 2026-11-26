#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# HB beta6 host checks: BoostRules (vendor-held Boost in Performance / Office), ZoomScale (stock dial, G0130 table,
# refused ends), wiring + the four fit / obstacle coupling directions. sh test/beta6/run.sh
set -eu
# in the repository this suite runs inside the HB app as its recipe assembles it (apps/LumenProjector/core.features)
if [ -f "$(dirname "$0")/../../../core.features" ]; then
  G=$(cd "$(dirname "$0")/../../../../.." && pwd); mkdir -p "$G/build"; A=$(mktemp -d "$G/build/hbt.XXXXXX")
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device goat_elegant --app HbProjector --out "$A" --quiet
  rc=0; LUMEN_GSI=$G sh "$A/test/$(basename "$(dirname "$0")")/run.sh" || rc=$?; rm -rf "$A"; exit $rc
fi
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/beta6
FP=$HERE/src/org/lumen/projector/FocusPattern.java   # the assembled tree, else the core of the one projector app
[ -f "$FP" ] || FP=$(cd "$HERE/../.." && pwd)/apps/LumenProjector/core/src/org/lumen/projector/FocusPattern.java
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/BoostRules.java" "$HERE/src/org/lumen/projector/panel/BoostLeave.java" "$HERE/src/org/lumen/projector/panel/BoostOutcome.java" "$HERE/src/org/lumen/projector/panel/ZoomScale.java" \
  "$FP" \
  "$T/BoostRulesTest.java" "$T/ZoomScaleTest.java" "$T/FocusPatternTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.BoostRulesTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.ZoomScaleTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.FocusPatternTest
# StrNoticeRules with the real StrGate (Z9X file the recipe takes) and the power test stubs
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
# the notice is a core class since the one projector app (the assembled tree of build.sh has it in src/)
SN=$HERE/src/org/lumen/projector/power; [ -f "$SN/StrNoticeRules.java" ] || SN=$GSI/apps/LumenProjector/core/src/org/lumen/projector/power
SG=$HERE/src/org/lumen/projector/power/StrGate.java
[ -f "$SG" ] || SG=$GSI/apps/LumenProjector/core/src/org/lumen/projector/power/StrGate.java
PS=$HERE/test/power/stubs
OUT2=$(mktemp -d)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT2" "$SG" \
  "$SN/StrNoticeRules.java" "$SN/StrNoticeKind.java" "$T/StrNoticeTest.java" \
  "$PS/android/content/Context.java" "$PS/android/content/BroadcastReceiver.java" "$PS/android/content/Intent.java" \
  "$PS/android/content/IntentFilter.java" "$PS/android/os/SystemProperties.java" "$PS/android/os/SystemClock.java" \
  "$PS/android/util/Log.java"
"$JAVA_HOME/bin/java" -cp "$OUT2" org.lumen.projector.power.StrNoticeTest
rm -rf "$OUT2"
python3 "$T/check_wiring.py"
echo "beta6: all passed"
