#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The disabled row look on an EMULATOR, never a user's projector (test/ui/grey_render.py): per model family the test
# APK of its real assembled app (test/ui/build.sh WALK=GreyRowsActivity) shows the picture mode list on the dark and
# the light menu for content kinds that grey some modes; the screenshots go to OUT.
#   sh run_grey.sh SERIAL [OUT] [DEVICE:APP:SIGNALS ...]   (SIGNALS comma separated)
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
[ $# -gt 0 ] || set -- horse_popular:Z9xProjector:sdr,dolby goat_elegant:HbProjector:sdr,dolby \
  g0111:X50Projector:sdr,dolby,imax
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
mkdir -p "$OUT"
bad=0
for m in "$@"; do
  d=${m%%:*} rest=${m#*:}; a=${rest%%:*} sigs=${rest#*:}
  WALK=GreyRowsActivity bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/grey_render.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" "$OUT" "$d" $(echo "$sigs" | tr , ' ') || bad=1
done
exit $bad
