#!/bin/sh
# HB beta6 host checks: BoostRules (vendor-held Boost in Performance / Office), ZoomScale (stock dial, G0130 table,
# refused ends), wiring + the four fit / obstacle coupling directions. sh test/beta6/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/beta6
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/BoostRules.java" "$HERE/src/org/lumen/projector/panel/ZoomScale.java" \
  "$HERE/src/org/lumen/projector/FocusPattern.java" \
  "$T/BoostRulesTest.java" "$T/ZoomScaleTest.java" "$T/FocusPatternTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.BoostRulesTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.ZoomScaleTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.FocusPatternTest
# StrNoticeRules with the real StrGate (Z9X file the recipe takes) and the power test stubs
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
SG=$HERE/src/org/lumen/projector/power/StrGate.java
[ -f "$SG" ] || SG=$GSI/apps/Z9xProjector/src/org/lumen/projector/power/StrGate.java
PS=$HERE/test/power/stubs
OUT2=$(mktemp -d)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT2" "$SG" \
  "$HERE/src/org/lumen/projector/power/StrNoticeRules.java" "$T/StrNoticeTest.java" \
  "$PS/android/content/Context.java" "$PS/android/content/BroadcastReceiver.java" "$PS/android/content/Intent.java" \
  "$PS/android/content/IntentFilter.java" "$PS/android/os/SystemProperties.java" "$PS/android/os/SystemClock.java" \
  "$PS/android/util/Log.java"
"$JAVA_HOME/bin/java" -cp "$OUT2" org.lumen.projector.power.StrNoticeTest
rm -rf "$OUT2"
python3 "$T/check_wiring.py"
echo "beta6: all passed"
