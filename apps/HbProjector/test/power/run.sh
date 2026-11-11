#!/bin/sh
# hb-t7 sleep decision host check (power/StrGate with stubs for the few Android classes it uses) against the image
# props devices/goat_elegant/image/hb_lumen_prop.txt: sh test/power/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
# the gsi/ tree: LUMEN_GSI when set (the test runs from the assembled tree of the recipe, outside apps/), else the
# folder two levels up
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
T=$HERE/test/power
# StrGate has no HB copy: run from apps/HbProjector (not the assembled tree), take the file the recipe takes
SRC=$HERE/src/org/lumen/projector/power/StrGate.java
[ -f "$SRC" ] || SRC=$GSI/apps/Z9xProjector/src/org/lumen/projector/power/StrGate.java
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
