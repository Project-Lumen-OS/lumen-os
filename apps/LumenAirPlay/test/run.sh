#!/bin/sh
# Lumen AirPlay host checks (plain Java, no android.jar): the casting screen's rules and its model table (the generated
# assets/cast/models.tsv must be current: tools/devices/cast_models.py check), then the wiring of the casting screen
# (manifest entry, the menu's action, the Miracast control permission). sh test/run.sh ; exit 0 = all passed.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror -proc:none -d "$OUT" \
  "$HERE"/src/org/lumen/airplay/cast/*.java "$HERE"/test/org/lumen/airplay/cast/*.java
# the shipping models: every model code (device.conf models=) of a device with an image recipe, from the registry
SHIPPING=$(python3 -B - "$HERE/../.." <<'PY'
import os, sys
sys.path.insert(0, os.path.join(sys.argv[1], "tools", "devices"))
import devices
out = []
for c in sorted(os.listdir(os.path.join(sys.argv[1], "devices"))):
    if os.path.isfile(os.path.join(sys.argv[1], "devices", c, "build.conf")):
        m = devices.get(sys.argv[1], c).get("models", "")
        out += m.split() if isinstance(m, str) else list(m)
print(" ".join(sorted(set(out))))
PY
)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.airplay.cast.CastTest "$HERE/assets/cast/models.tsv" "$SHIPPING"
python3 -B "$G/tools/devices/cast_models.py" --root "$G" check
M="$HERE/AndroidManifest.xml"
bad=0
grep -q '<action android:name="org.lumen.cast.SCREEN" />' "$M" || { echo "FAIL manifest: no org.lumen.cast.SCREEN"; bad=1; }
grep -A12 'android:name=".CastActivity"' "$M" | grep -q 'android.intent.category.LEANBACK_LAUNCHER' \
  || { echo "FAIL manifest: the casting screen has no Home tile (LEANBACK_LAUNCHER)"; bad=1; }
grep -q 'uses-permission android:name="org.lumen.miracast.permission.CONTROL"' "$M" \
  || { echo "FAIL manifest: no Miracast control permission"; bad=1; }
grep -q 'CASTING_ACTION = "org.lumen.cast.SCREEN"' "$G/apps/core/lumen-menu/src/org/lumen/projector/panel/ModuleScreens.java" \
  || { echo "FAIL menu: the casting row opens another action"; bad=1; }
grep -q '"org.lumen.airplay"' "$G/apps/LumenHome/src/org/lumen/home/model/AppOrder.java" \
  && { echo "FAIL home: org.lumen.airplay hidden by default, no tile"; bad=1; }
[ "$bad" = 0 ] || exit 1
echo "airplay run.sh: ALL PASSED"
