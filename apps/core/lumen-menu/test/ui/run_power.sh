#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Power key walk on an EMULATOR (never a user's projector): builds the power walk APK of each model (test/ui/build.sh,
# WALK=PowerWalkActivity) and runs test/ui/power_walk.py (the setting "Short press of the power key" on the real power
# key code of each family, with a virtual HID remote).
#   sh apps/core/lumen-menu/test/ui/run_power.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
: "${OUT:?}"
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector horse_popular:Z9xProjector goat_elegant:HbProjector dragon_healthy:Play6Projector
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null </dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null </dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  WALK=PowerWalkActivity EXTRA_SRC="org/lumen/x50/projector/X50Power.java org/lumen/projector/Family.java org/lumen/projector/panel/ModuleScreens.java" \
    EXTRA_PERMS="android.permission.DEVICE_POWER android.permission.INJECT_EVENTS android.permission.SYSTEM_ALERT_WINDOW android.permission.REBOOT android.permission.SCHEDULE_EXACT_ALARM" \
    bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null </dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/power_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" </dev/null || bad=1
done
exit $bad
