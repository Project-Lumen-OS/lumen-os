#!/bin/bash
# The casting page key walk on an emulator (never a projector): builds the test APKs (test/ui/build.sh), installs
# them and runs test/ui/cast_walk.py. One APK serves every model, so one walk covers them all.
#   bash apps/LumenAirPlay/test/ui/run.sh SERIAL OUT      (adb from $ADB; exit 0 = every check passed)
set -eu
S=$1 OUT=$2 ADB=${ADB:-adb}
H=$(cd "$(dirname "$0")" && pwd)
bash "$H/build.sh" "$OUT"
for n in miracast airplay; do "$ADB" -s "$S" install -r -g "$OUT/$n.apk" > /dev/null; done
python3 "$H/cast_walk.py" "$S" "$(cd "$H/../.." && pwd)" "$OUT"
