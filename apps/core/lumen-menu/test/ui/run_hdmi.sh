#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The HDMI page, Licences page and Back to the XGIMI system key walks (test/ui/hdmi_walk.py, licences_walk.py, xgimi_walk.py) on an EMULATOR, never a user's projector: the fake port app
# (apps/core/lumen-hdmiports/test/ui/build_fake.sh) answers as the HDMI app, then per model the test APK of its real
# assembled app (test/ui/build.sh) is installed and walked.   sh run_hdmi.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector goat_elegant:HbProjector horse_popular:Z9xProjector dragon_healthy:Play6Projector
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
bash "$G/apps/core/lumen-hdmiports/test/ui/build_fake.sh" "$OUT" >/dev/null
"$ADB" -s "$SERIAL" install -r "$OUT/fakeports.apk" >/dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  HAS_CEC=1; [ -f "$OUT/$d/tree/src/org/lumen/projector/cec/CecPage.java" ] || HAS_CEC=0   # the app carries lumen-cec
  HAS_CEC=$HAS_CEC python3 "$HERE/hdmi_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" || bad=1
  python3 "$HERE/licences_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" || bad=1
  python3 "$HERE/xgimi_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" || bad=1
done
exit $bad
