#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Menu values after a device row changes, walk on an EMULATOR (never a user's projector): builds the menu walk APK of
# each model (test/ui/build.sh, MenuWalkActivity) and runs test/ui/async_rows_walk.py (All settings > General, the
# model's device row of the power group changed and put back, every row of the menu keeps its value: 'Sound at
# power-on', key tone, screen saver, language, date, storage, version, model, volume). Any android-34 AVD.
#   sh apps/core/lumen-menu/test/ui/run_async_rows.sh SERIAL [OUT] [DEVICE:APP:FEATURE ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
: "${OUT:?}"
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
[ $# -gt 0 ] || set -- horse_popular:Z9xProjector:power.boot_source goat_elegant:HbProjector:power.boot_source \
  dragon_healthy:Play6Projector:power.boot_source g0086:Z9xProjector:power.boot_source \
  g0111:X50Projector:power.on_ac g0104:X50Projector:power.on_ac
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null </dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null </dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} r=${m#*:} a=${r%%:*} f=${r#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null </dev/null
  p=$(cat "$OUT/$d/package")
  "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$p" --locales en >/dev/null </dev/null
  echo "== $d ($a, $p, $f)"
  python3 "$HERE/async_rows_walk.py" "$SERIAL" "$p" "$f" </dev/null || bad=1
done
exit $bad
