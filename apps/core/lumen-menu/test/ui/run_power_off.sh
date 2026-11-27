#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Power off on an EMULATOR (never a user's projector): per model a fresh emulator boot ($EMU_UP), the power walk APK of
# the model (test/ui/build.sh, WALK=PowerWalkActivity, as run_power.sh), then power_walk.py POWER_OFF=1: the virtual
# power key opens the round power menu, RIGHT to Power off, OK, and Android's shutdown must run (ShutdownThread, never
# the power key's sleep of report LR-YVHNX4). The emulator powers off, so one boot per model; $EMU_DOWN cleans up.
# The kept log lines of each model: OUT/<device>/poweroff.log (the replay fixtures testdata/emu_*_poweroff.txt).
#   EMU_UP='sh emu.sh up po 5640' EMU_DOWN='sh emu.sh down po 5640' sh run_power_off.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
: "${OUT:?}" "${EMU_UP:?}" "${EMU_DOWN:?}"
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector horse_popular:Z9xProjector goat_elegant:HbProjector dragon_healthy:Play6Projector
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  mkdir -p "$OUT/$d"
  WALK=PowerWalkActivity EXTRA_SRC="org/lumen/x50/projector/X50Power.java org/lumen/projector/Family.java org/lumen/projector/panel/ModuleScreens.java" \
    EXTRA_PERMS="android.permission.DEVICE_POWER android.permission.INJECT_EVENTS android.permission.SYSTEM_ALERT_WINDOW android.permission.REBOOT" \
    bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  sh -c "$EMU_UP" </dev/null || { echo "FAIL $d: no emulator"; bad=1; continue; }
  "$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null </dev/null
  "$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null </dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null </dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  POWER_OFF=1 POWER_OFF_LOG="$OUT/$d/poweroff.log" python3 "$HERE/power_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" </dev/null || bad=1
  sh -c "$EMU_DOWN" </dev/null >/dev/null 2>&1 || true
done
[ "$bad" = 0 ] && echo "run_power_off: all green" || { echo "run_power_off: RED"; exit 1; }
