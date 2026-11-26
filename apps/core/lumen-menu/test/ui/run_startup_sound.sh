#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Power-on sound row walk on an EMULATOR (never a user's projector): builds the menu walk APK of each model
# (test/ui/build.sh, MenuWalkActivity) and runs test/ui/startup_sound_walk.py (All settings > General > Power > Sound at
# power-on, OK twice, the switch and boot animation gate props checked). The emulator must let the app write
# persist.lumen.*: an android-34 google_apis x86_64 (userdebug) AVD started with -selinux permissive.
#   sh apps/core/lumen-menu/test/ui/run_startup_sound.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
: "${OUT:?}"
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector horse_popular:Z9xProjector goat_elegant:HbProjector dragon_healthy:Play6Projector \
  g0086:Z9xProjector g0104:X50Projector
[ "$("$ADB" -s "$SERIAL" shell getenforce </dev/null | tr -d '\r')" = Permissive ] || { echo "the emulator enforces SELinux: the walk cannot set persist.lumen.*"; exit 2; }
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null </dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null </dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null </dev/null
  p=$(cat "$OUT/$d/package")
  "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$p" --locales en >/dev/null </dev/null
  echo "== $d ($a, $p)"
  python3 "$HERE/startup_sound_walk.py" "$SERIAL" "$p" </dev/null || bad=1
done
exit $bad
