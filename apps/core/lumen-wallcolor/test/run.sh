#!/bin/sh
# lumen-wallcolor host tests: sh apps/core/lumen-wallcolor/test/run.sh
#  1. WallColorTest: the vendor's 11001 messages, the stock flow and its HAL order, the pattern geometry and timing,
#     every model file (devices/*/wallcolor.conf: card text variant, pattern type);
#  2. every message has a text and every text key is in all 18 locale files of the module;
#  3. the module never names a vendor call: the HAL is the app's (WallColorHal), the codes live in hal/codes.tsv;
#  4. no model sends a factory calibration call (startAutoColorCalibration, its check, its data) anywhere in the apps.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_wallcolor.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
bad=0
S=$HERE/src/org/lumen/wallcolor
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S/WallColorStep.java" "$S/WallColorHal.java" \
  "$S/WallColorEvent.java" "$S/WallColorMessage.java" "$S/WallColorView.java" "$S/WallColorFlow.java" \
  "$S/WallColorPattern.java" "$S/WallColorModel.java" "$HERE/test/WallColorTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.wallcolor.WallColorTest \
  "$G/devices/horse_popular/wallcolor.conf" false 3 "$G/devices/goat_elegant/wallcolor.conf" false 3 \
  "$G/devices/g0111/wallcolor.conf" true 3 "$G/devices/g0100/wallcolor.conf" true 3 \
  "$G/devices/g0155/wallcolor.conf" true 5 "$G/devices/h0062/wallcolor.conf" true 3 \
  "$G/devices/h0063/wallcolor.conf" true 3 || bad=1
python3 - "$HERE" <<'PY' || bad=1
import os, re, sys
here = sys.argv[1]
win = open(os.path.join(here, "src/org/lumen/wallcolor/WallColorWindows.java")).read()
msg = open(os.path.join(here, "src/org/lumen/wallcolor/WallColorMessage.java")).read()
names = re.findall(r"^    ([A-Z_0-9]+)\(-?\d+\)", msg, re.M)
ok = True
for n in names:
    if n != "OUT_OF_RANGE" and ("case %s:" % n) not in win:
        print("FAIL message %s has no text" % n); ok = False
used = set(re.findall(r"R\.string\.(lumen_wallcolor_[a-z0-9_]+)", win))
base = set(re.findall(r'name="(lumen_wallcolor_[a-z0-9_]+)"', open(os.path.join(here, "res/values/lumen_strings_wallcolor.xml")).read()))
for u in sorted(used - base): print("FAIL string %s missing" % u); ok = False
dirs = sorted(d for d in os.listdir(os.path.join(here, "res")) if d.startswith("values-"))
if len(dirs) != 17: print("FAIL %d locales, want 17" % len(dirs)); ok = False
for d in dirs:
    p = os.path.join(here, "res", d, "lumen_strings_wallcolor.xml")
    have = set(re.findall(r'name="(lumen_wallcolor_[a-z0-9_]+)"', open(p).read())) if os.path.exists(p) else set()
    if have != base: print("FAIL %s: %s" % (d, sorted(base ^ have))); ok = False
    if re.search(r"[;\u2013\u2014]", open(p).read()): print("FAIL %s: semicolon or dash" % d); ok = False
print(("ok  " if ok else "FAIL") + " texts: %d messages, %d strings, %d locales" % (len(names), len(base), len(dirs)))
sys.exit(0 if ok else 1)
PY
if grep -rn -E "startAutoColor|setAutoColor|setAEOnOff|IDisplayManager|IGmpf" "$HERE/src" | grep -v "^\S*:[0-9]*:\s*\*\|//\|/\*\*" ; then
  echo "FAIL the module names a vendor call outside comments"; bad=1
else
  echo "ok   the module makes no vendor call itself"
fi
if grep -rn -E "START_AUTO_COLOR_CALIBRATION|AUTO_COLOR_CALIBRATION_CHECK|GET_AUTO_COLOR_CALIBRATION|startAutoColorCalibration|getAutoColorCalibration" \
    "$G/apps/ProjectorBase/src" "$G/apps/HbProjector/src" "$G/apps/X50Projector/src" "$G/apps/core/lumen-gmpf/src" "$G/devices"/*/hal/codes.tsv 2>/dev/null; then
  echo "FAIL a factory calibration call is wired"; bad=1
else
  echo "ok   no factory calibration call in any projector app or code table"
fi
[ $bad = 0 ] && echo "PASS lumen-wallcolor" || echo "FAIL lumen-wallcolor"
exit $bad
