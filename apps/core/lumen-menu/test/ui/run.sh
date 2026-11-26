#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# All settings > General key walk on an EMULATOR (never a user's projector), one X50 family and one Z9X build: builds
# the test APK of each (test/ui/build.sh: the model's real assembled app with the walk activity), installs it and runs
# test/ui/general_walk.py.   sh apps/core/lumen-menu/test/ui/run.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector horse_popular:Z9xProjector
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/general_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" || bad=1
done
exit $bad
