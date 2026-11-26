#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The Parental controls page key walk (test/ui/parental_walk.py) on an EMULATOR, never a user's projector: Lumen Home
# of this tree holds the state (ParentalProvider, a signature permission), so it is built here and signed with the
# same public test platform key as the walk APKs (apps/sdk), then per model the test APK of its real assembled app
# (test/ui/build.sh) is installed and walked.   sh run_parental.sh SERIAL [OUT] [DEVICE:APP ...]
set -eu
SERIAL=${1:?serial}; shift
OUT=${1:-$(mktemp -d)}; [ $# -gt 0 ] && shift
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
[ $# -gt 0 ] || set -- g0111:X50Projector goat_elegant:HbProjector horse_popular:Z9xProjector dragon_healthy:Play6Projector
mkdir -p "$OUT/home"
python3 -B "$G/tools/devices/assemble_app.py" "$G/apps/LumenHome/app.layers" --out "$OUT/home/tree" --quiet
KEY_PK8="$G/apps/sdk/platform.pk8" KEY_PEM="$G/apps/sdk/platform.x509.pem" BUILD_DIR="$OUT/home/b" \
  sh "$G/apps/build_apk.sh" "$OUT/home/tree" "$OUT/home/home.apk" > "$OUT/home/build.log" 2>&1
"$ADB" -s "$SERIAL" install -r -g "$OUT/home/home.apk" >/dev/null
bad=0
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/parental_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$G" || bad=1
done
exit $bad
