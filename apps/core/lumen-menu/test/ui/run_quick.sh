#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Quick menu key walk on an EMULATOR (never a user's projector): every setting of the full menu can be put into the
# quick menu (2026-11-24).
# Builds the test APK of each model (test/ui/build.sh, WALK=QuickWalkActivity: the model's real assembled app),
# installs it and runs test/ui/quick_walk.py:
#   X50 Ultra G0111: MEMC, dynamic black and AI contrast as tiles, changed, after a restart, reset;
#   Z9X G0082: MEMC and AI contrast, no dynamic black offered;
#   G0111 with picture.memc taken out of its profile: MEMC not offered, a stored menu with it shows no MEMC tile.
#   sh apps/core/lumen-menu/test/ui/run_quick.sh SERIAL [OUT [SHOTS]]
set -eu
SERIAL=${1:?serial}
OUT=${2:-$(mktemp -d)}
SHOTS=${3:-$OUT/shots}
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put system accelerometer_rotation 0 >/dev/null
SEED='{"v":1,"density":"tiles","items":[{"id":"network","as":"round"},{"id":"theme","as":"round"},{"id":"edit","as":"round"},{"id":"general","as":"round"},{"id":"picture.memc","as":"wide"},{"id":"picture.dynamic_black","as":"small"}],"seen":[]}'
bad=0
walk() { # name device app walk-arguments...
  n=$1 d=$2 a=$3; shift 3
  WALK=QuickWalkActivity bash "$HERE/build.sh" "$OUT/$n" "$d" "$a" >/dev/null
  pkg=$(cat "$OUT/$n/package")
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$n/walk.apk" >/dev/null
  "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$pkg" --locales en-US
  mkdir -p "$SHOTS/$n"
  echo "== $n ($d $a, $pkg)"
  python3 "$HERE/quick_walk.py" "$SERIAL" "$pkg" "$@" --shots "$SHOTS/$n" || bad=1
}
walk x50 g0111 X50Projector picture.memc picture.dynamic_black --also picture.ai_contrast --via "Professional settings"
walk z9x horse_popular Z9xProjector picture.memc picture.ai_contrast --absent picture.dynamic_black
WITHOUT=picture.memc
export WITHOUT
walk x50_no_memc g0111 X50Projector - - --absent picture.memc --seed "$SEED"
exit $bad
